package orochi.steelsight.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.CheckInCorrection;
import orochi.steelsight.attendance.repository.CheckInCorrectionRepository;
import orochi.steelsight.attendance.repository.CheckInRepository;
import orochi.steelsight.calendar.MonthLocks;
import orochi.steelsight.calendar.SiteCalendar;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.notification.model.NotificationKind;
import orochi.steelsight.notification.service.NotificationService;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.repository.ShiftRepository;
import orochi.steelsight.ticketing.model.CategoryCode;
import orochi.steelsight.ticketing.model.Direction;
import orochi.steelsight.ticketing.model.LeaveType;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketCategory;
import orochi.steelsight.ticketing.model.TicketComment;
import orochi.steelsight.ticketing.model.TicketOutcome;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.repository.TicketCategoryRepository;
import orochi.steelsight.ticketing.repository.TicketCommentRepository;
import orochi.steelsight.ticketing.repository.TicketRepository;
import orochi.steelsight.validation.Text;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tickets (SRS UC-12 to UC-18, UC-24, BR-60 to BR-69). Every ticket goes to the Admin queue; an Admin
 * never decides their own. The team leader of the creator can read and comment; an HSE Officer can when
 * the Admin asks for the facts. Each category has its own rules when it is created and when it is decided.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    static final Duration ATTENDANCE_ERROR_PERIOD = Duration.ofHours(48);
    private static final Set<TicketStatus> UNRESOLVED = EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final AccessPolicy access;
    private final TicketRepository tickets;
    private final TicketCategoryRepository categories;
    private final TicketCommentRepository comments;
    private final ViolationRepository violations;
    private final ViolationService violationService;
    private final ShiftRepository shifts;
    private final CheckInRepository checkIns;
    private final CheckInCorrectionRepository corrections;
    private final AppUserRepository users;
    private final NotificationService notifications;
    private final MonthLocks locks;
    private final SiteCalendar calendar;
    private final JdbcTemplate jdbc;

    /**
     * What a new ticket says. The fields after description are filled by category: shiftId for an
     * attendance error and a leave request, checkInId for a missing check-out (SRS 3.2.20).
     */
    public record NewTicket(Long categoryId, String subject, String description, String photoPath, Long shiftId,
                            Long checkInId, Direction direction, OffsetDateTime gateTime, LeaveType leaveType,
                            OffsetDateTime leaveFrom, OffsetDateTime leaveTo) {

        public static NewTicket other(Long categoryId, String subject, String description) {
            return new NewTicket(categoryId, subject, description, null, null, null, null, null, null, null, null);
        }
    }

    /** A filter of the Ticket Queue or My Tickets. status null means unresolved. */
    public record QueueFilter(Long categoryId, TicketStatus status) {
    }

    // --- creating ------------------------------------------------------------------------------------

    public Ticket create(Long actorId, Long categoryId, String subject, String description) {
        return create(actorId, NewTicket.other(categoryId, subject, description));
    }

    /** Opens a ticket of one of the five categories; a PPE appeal starts from its violation instead. */
    public Ticket create(Long actorId, NewTicket details) {
        access.require(actorId, Permissions.TICKET_CREATE);
        if (details.categoryId() == null) {
            throw new BusinessRuleException("Choose a category");
        }
        TicketCategory category = categories.findById(details.categoryId())
                .orElseThrow(() -> new NotFoundException("Ticket category", details.categoryId()));
        if (!category.isActive() || category.getCode() == null) {
            throw new BusinessRuleException("Choose a category");
        }
        if (category.is(CategoryCode.PPE_APPEAL)) {
            throw new BusinessRuleException("Open a PPE appeal from the violation itself");
        }
        Ticket ticket = newTicket(actorId, category, details.subject(), details.description());
        ticket.setPhotoPath(details.photoPath());
        switch (category.getCode()) {
            case ATTENDANCE_ERROR -> attendanceError(actorId, ticket, details);
            case MISSING_CHECK_OUT -> missingCheckOut(actorId, ticket, details);
            case LEAVE -> leave(actorId, ticket, details);
            default -> { }
        }
        return saveNew(ticket);
    }

    /** BR-66: the day, the shift, IN or OUT, the time at the gate and a photo; within 48 hours of the shift end. */
    private void attendanceError(Long actorId, Ticket ticket, NewTicket details) {
        Shift shift = ownShift(actorId, details.shiftId());
        if (details.direction() == null) {
            throw new BusinessRuleException("Choose IN or OUT");
        }
        if (details.gateTime() == null) {
            throw new BusinessRuleException("Enter the time you were at the gate");
        }
        if (details.photoPath() == null) {
            throw new BusinessRuleException("Attach a photo taken at the gate.");
        }
        OffsetDateTime ends = shift.getEndAt().plus(ATTENDANCE_ERROR_PERIOD);
        if (OffsetDateTime.now().isAfter(ends)) {
            throw periodEnded(ends);
        }
        locks.requireOpen(shift.getStartAt());
        ticket.setShift(shift);
        ticket.setDirection(details.direction());
        ticket.setGateTime(details.gateTime());
    }

    /** BR-67: only for a visit flagged Incomplete, within 3 working days of the flag. */
    private void missingCheckOut(Long actorId, Ticket ticket, NewTicket details) {
        if (details.checkInId() == null) {
            throw new BusinessRuleException("Choose the visit to explain");
        }
        CheckIn visit = checkIns.require(details.checkInId());
        if (!visit.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("This is someone else's visit");
        }
        if (!visit.isIncomplete()) {
            throw new BusinessRuleException("Only a visit with incomplete attendance can be explained");
        }
        OffsetDateTime ends = calendar.deadline(visit.getIncompleteAt(), explanationDays());
        if (OffsetDateTime.now().isAfter(ends)) {
            throw periodEnded(ends);
        }
        locks.requireOpen(visit.getCheckInAt());
        ticket.setCheckIn(visit);
        ticket.setShift(visit.getShift());
    }

    /** BR-68: paid or unpaid, inside the shift, sent before the shift ends. */
    private void leave(Long actorId, Ticket ticket, NewTicket details) {
        Shift shift = ownShift(actorId, details.shiftId());
        if (details.leaveType() == null) {
            throw new BusinessRuleException("Choose paid or unpaid leave");
        }
        if (!OffsetDateTime.now().isBefore(shift.getEndAt())) {
            throw new BusinessRuleException("A leave request must be sent before the shift ends.");
        }
        OffsetDateTime from = details.leaveFrom();
        OffsetDateTime to = details.leaveTo();
        if (from == null || to == null || !to.isAfter(from) || from.isBefore(shift.getStartAt()) || to.isAfter(shift.getEndAt())) {
            throw new BusinessRuleException("The leave must be inside the shift and the end must be after the start.");
        }
        ticket.setShift(shift);
        ticket.setLeaveType(details.leaveType());
        ticket.setLeaveFrom(from);
        ticket.setLeaveTo(to);
    }

    /**
     * Appeals a confirmed violation (BR-64): marks it DISPUTED and opens its ticket in one transaction.
     * The points stay deducted until the Admin decides.
     */
    public Ticket openDispute(Long actorId, Long violationId, String subject, String description) {
        access.require(actorId, Permissions.TICKET_CREATE);
        Violation violation = violations.require(violationId);
        if (!violation.belongsTo(actorId)) {
            throw new AccessDeniedException("You can only appeal your own violation");
        }
        violationService.markAppealed(actorId, violation);
        TicketCategory category = categories.findByCode(CategoryCode.PPE_APPEAL)
                .orElseThrow(() -> new NotFoundException("Ticket category", CategoryCode.PPE_APPEAL));
        String title = subject == null || subject.isBlank()
                ? "Appeal - violation " + WHEN.format(calendar.local(violation.getDetectedAt()))
                : subject;
        Ticket ticket = newTicket(actorId, category, title, description);
        ticket.setViolation(violation);
        return saveNew(ticket);
    }

    private Ticket saveNew(Ticket ticket) {
        Ticket saved;
        try {
            saved = tickets.saveAndFlush(ticket);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("You already have an open request for this. Open it to add more details.");
        }
        List<Long> admins = activeAdmins();
        admins.remove(saved.getCreatedBy().getId());
        notifications.notifyAll(admins, NotificationKind.TICKET_CREATED,
                Map.of("subject", saved.getSubject(), "creator", saved.getCreatedBy().getFullName()), link(saved));
        return saved;
    }

    private Shift ownShift(Long actorId, Long shiftId) {
        if (shiftId == null) {
            throw new BusinessRuleException("Choose the day and shift");
        }
        Shift shift = shifts.require(shiftId);
        if (!shift.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("This is someone else's shift");
        }
        return shift;
    }

    private BusinessRuleException periodEnded(OffsetDateTime ends) {
        return new BusinessRuleException("The period for this request has ended.");
    }

    private int explanationDays() {
        Integer days = jdbc.queryForObject("SELECT explanation_working_days FROM company_setting", Integer.class);
        return days == null ? 3 : days;
    }

    // --- reading -------------------------------------------------------------------------------------

    /** The five categories a new ticket can have, without the appeal, which starts from a violation. */
    @Transactional(readOnly = true)
    public List<TicketCategory> listCategoriesForNewTicket(Long actorId) {
        access.require(actorId, Permissions.TICKET_CREATE);
        return categories.findAllByActiveTrueOrderByIdAsc().stream()
                .filter(category -> category.getCode() != null && !category.is(CategoryCode.PPE_APPEAL))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TicketCategory> category(CategoryCode code) {
        return categories.findByCode(code);
    }

    /** The live appeal ticket of a violation, if it has one and the actor may read it. */
    @Transactional(readOnly = true)
    public Optional<Ticket> findDispute(Long actorId, Long violationId) {
        return tickets.findByViolationIdAndDeletedAtIsNull(violationId)
                .filter(ticket -> access.canReadTicket(actorId, ticket.getId()));
    }

    @Transactional(readOnly = true)
    public Ticket get(Long actorId, Long ticketId) {
        access.requireCanReadTicket(actorId, ticketId);
        return tickets.require(ticketId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listOwn(Long actorId) {
        access.require(actorId, Permissions.TICKET_READ_OWN);
        return tickets.findAllByCreatedByIdAndDeletedAtIsNullOrderByCreatedAtDesc(actorId);
    }

    @Transactional(readOnly = true)
    public Page<Ticket> listOwn(Long actorId, Pageable page) {
        return listOwn(actorId, null, page);
    }

    /** SRS 3.2.21. status null shows every status. */
    @Transactional(readOnly = true)
    public Page<Ticket> listOwn(Long actorId, TicketStatus status, Pageable page) {
        access.require(actorId, Permissions.TICKET_READ_OWN);
        Specification<Ticket> spec = live().and((root, query, cb) -> cb.equal(root.get("createdBy").get("id"), actorId));
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return tickets.findAll(spec, PageRequest.of(page.getPageNumber(), page.getPageSize(), Sort.by("createdAt", "id").descending()));
    }

    /** Tickets opened by the team the actor leads, which they may read and comment on (SRS BR-61). */
    @Transactional(readOnly = true)
    public Page<Ticket> listTeam(Long actorId, Pageable page) {
        access.require(actorId, Permissions.TICKET_READ_TEAM);
        return tickets.findTeamTickets(actorId, page);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listHandled(Long actorId) {
        access.require(actorId, Permissions.TICKET_READ_HANDLED);
        return tickets.findAllByHandlerRoleId(users.require(actorId).getRole().getId());
    }

    @Transactional(readOnly = true)
    public List<Ticket> listAll(Long actorId) {
        access.require(actorId, Permissions.TICKET_READ_ALL);
        return tickets.findAllByDeletedAtIsNull();
    }

    public Page<Ticket> listQueue(Long actorId, boolean resolved, Pageable page) {
        return listQueue(actorId, new QueueFilter(null, resolved ? TicketStatus.RESOLVED : null), page);
    }

    public Page<Ticket> listQueue(Long actorId, boolean resolved, boolean everyCategory, Pageable page) {
        return listQueue(actorId, resolved, page);
    }

    /**
     * SRS 3.2.22: the Admin queue. Unresolved tickets come oldest first, because they have waited
     * longest; resolved and cancelled ones newest first.
     */
    @Transactional(readOnly = true)
    public Page<Ticket> listQueue(Long actorId, QueueFilter filter, Pageable page) {
        access.requireAny(actorId, Permissions.TICKET_READ_HANDLED, Permissions.TICKET_READ_ALL);
        Specification<Ticket> spec = live();
        Sort sort;
        if (filter.status() == null) {
            spec = spec.and((root, query, cb) -> root.get("status").in(UNRESOLVED));
            sort = Sort.by("createdAt", "id");
        } else {
            TicketStatus status = filter.status();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
            sort = status == TicketStatus.RESOLVED ? Sort.by("resolvedAt", "id").descending() : Sort.by("createdAt", "id").descending();
        }
        if (filter.categoryId() != null) {
            Long category = filter.categoryId();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), category));
        }
        if (!access.has(actorId, Permissions.TICKET_READ_ALL)) {
            Long roleId = users.require(actorId).getRole().getId();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("handlerRole").get("id"), roleId));
        }
        return tickets.findAll(spec, PageRequest.of(page.getPageNumber(), page.getPageSize(), sort));
    }

    /** Unresolved tickets in the actor's queue. */
    @Transactional(readOnly = true)
    public long countUnresolvedInQueue(Long actorId) {
        access.requireAny(actorId, Permissions.TICKET_READ_HANDLED, Permissions.TICKET_READ_ALL);
        Long roleId = users.require(actorId).getRole().getId();
        return tickets.countByCategoryHandlerRoleIdAndStatusInAndDeletedAtIsNull(roleId, UNRESOLVED);
    }

    /** BR-69: working days since the ticket was created; red after 3. */
    public int ageInWorkingDays(Ticket ticket) {
        return calendar.workingDaysBetween(calendar.dateOf(ticket.getCreatedAt()), calendar.today());
    }

    @Transactional(readOnly = true)
    public boolean canHandle(Long actorId, Long ticketId) {
        return access.canUpdateTicket(actorId, ticketId);
    }

    @Transactional(readOnly = true)
    public boolean canComment(Long actorId, Long ticketId) {
        return access.canCommentTicket(actorId, ticketId);
    }

    /** Active Admins who can work on this ticket: never its creator (SRS UC-24 EX-01). */
    @Transactional(readOnly = true)
    public List<AppUser> listPossibleAssignees(Long actorId, Long ticketId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        return users.findActiveByRole(tickets.require(ticketId).getCategory().getHandlerRole().getId()).stream()
                .filter(candidate -> access.canUpdateTicket(candidate.getId(), ticketId))
                .toList();
    }

    /**
     * The HSE Officers the Admin may ask for the facts. For a PPE appeal the officer who confirmed the
     * violation and the officers on duty at that time come first (SRS 3.2.22 #4).
     */
    @Transactional(readOnly = true)
    public List<AppUser> listHseToAsk(Long actorId, Long ticketId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = tickets.require(ticketId);
        OffsetDateTime at = ticket.getViolation() != null ? ticket.getViolation().getDetectedAt()
                : ticket.getShift() != null ? ticket.getShift().getStartAt() : ticket.getCreatedAt();
        Long reviewer = ticket.getViolation() != null && ticket.getViolation().getReviewedBy() != null
                ? ticket.getViolation().getReviewedBy().getId() : null;
        List<Long> ids = jdbc.queryForList("""
                SELECT u.id FROM app_user u JOIN role r ON r.id = u.role_id
                WHERE r.role_name = 'HSE_OFFICER' AND u.is_active AND u.deleted_at IS NULL
                ORDER BY (u.id = ?) DESC,
                         EXISTS (SELECT 1 FROM shift s WHERE s.user_id = u.id AND s.status <> 'CANCELLED'
                                 AND ? >= s.start_at AND ? < s.end_at) DESC,
                         u.full_name, u.id""", Long.class, reviewer, at, at);
        List<AppUser> found = users.findAllById(ids);
        return ids.stream().map(id -> found.stream().filter(u -> u.getId().equals(id)).findFirst().orElseThrow()).toList();
    }

    @Transactional(readOnly = true)
    public List<AppUser> listParticipants(Long actorId, Long ticketId) {
        access.requireCanReadTicket(actorId, ticketId);
        return users.findAllById(jdbc.queryForList(
                "SELECT user_id FROM ticket_participant WHERE ticket_id = ? ORDER BY asked_at", Long.class, ticketId));
    }

    @Transactional(readOnly = true)
    public List<TicketComment> listComments(Long actorId, Long ticketId) {
        access.requireCanReadTicket(actorId, ticketId);
        return comments.findAllByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    // --- handling ------------------------------------------------------------------------------------

    /** "Take": IN_PROGRESS with this Admin, or another Admin who can handle it. */
    public Ticket assign(Long actorId, Long ticketId, Long assigneeId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        if (assigneeId == null || !access.canUpdateTicket(assigneeId, ticketId)) {
            throw new BusinessRuleException("That user cannot handle this ticket");
        }
        Ticket ticket = open(ticketId);
        ticket.assignTo(users.require(assigneeId));
        return tickets.saveAndFlush(ticket);
    }

    /** SRS 3.2.22 #4: the asked HSE Officer receives a notice and may comment. */
    public void askHse(Long actorId, Long ticketId, Long officerId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        boolean isHse = officerId != null && listHseToAsk(actorId, ticketId).stream().anyMatch(u -> u.getId().equals(officerId));
        if (!isHse) {
            throw new BusinessRuleException("Choose an HSE Officer");
        }
        jdbc.update("""
                INSERT INTO ticket_participant (ticket_id, user_id, asked_by) VALUES (?, ?, ?)
                ON CONFLICT (ticket_id, user_id) DO NOTHING""", ticketId, officerId, actorId);
        notifications.notify(officerId, NotificationKind.ASKED_FOR_FACTS, Map.of("subject", ticket.getSubject()), link(ticket));
    }

    /** What the HSE Officer of that shift said, written by the Admin (10 to 1,000 characters). */
    public Ticket saveHseFacts(Long actorId, Long ticketId, String facts) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        ticket.setHseFacts(Text.sized("The HSE facts", facts, 10, 1000));
        return tickets.saveAndFlush(ticket);
    }

    /**
     * Decides a request: Approve or Reject (SRS 3.2.22). Approving a missing check-out sets the
     * check-out to the shift end and removes the flag; approving a leave request makes the time leave.
     * An attendance error is approved from the Attendance Form, which corrects the visit with this ticket.
     */
    public Ticket decide(Long actorId, Long ticketId, TicketOutcome outcome, String note) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        if (ticket.isAppeal()) {
            throw new BusinessRuleException("Resolve a PPE dispute by deciding its violation");
        }
        if (outcome != TicketOutcome.APPROVED && outcome != TicketOutcome.REJECTED) {
            throw new BusinessRuleException("A request is approved or rejected");
        }
        String decisionNote = note(note);
        CategoryCode code = ticket.getCategory().getCode();
        if (code == CategoryCode.ATTENDANCE_ERROR) {
            requireFacts(ticket);
        }
        if (outcome == TicketOutcome.APPROVED && code != null) {
            switch (code) {
                case ATTENDANCE_ERROR -> throw new BusinessRuleException("Approve an attendance error by correcting the attendance");
                case MISSING_CHECK_OUT -> approveMissingCheckOut(actorId, ticket);
                case LEAVE -> locks.requireOpen(ticket.getShift().getStartAt());
                default -> { }
            }
        }
        return finish(actorId, ticket, outcome, decisionNote);
    }

    /** Called by the Attendance Form after it saved the correction an attendance error asked for. */
    public Ticket approveAttendanceError(Long actorId, Long ticketId, String note) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        if (!ticket.getCategory().is(CategoryCode.ATTENDANCE_ERROR)) {
            throw new BusinessRuleException("This ticket is not an attendance error");
        }
        requireFacts(ticket);
        return finish(actorId, ticket, TicketOutcome.APPROVED, note(note == null || note.isBlank() ? ticket.getDecisionNote() : note));
    }

    /** Saves the decision note before the Attendance Form opens, so the form can resolve the ticket. */
    public Ticket prepareAttendanceApproval(Long actorId, Long ticketId, String note) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        requireFacts(ticket);
        ticket.setDecisionNote(note(note));
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.assignTo(users.require(actorId));
        }
        return tickets.saveAndFlush(ticket);
    }

    /**
     * Decides an appeal (BR-65): the HSE facts and a note are required. UPHELD keeps the points;
     * OVERTURNED gives them back, and with a named person the violation goes back to the queue for them.
     */
    public Violation decideAppeal(Long actorId, Long violationId, DisputeStatus decision, String hseFacts, String note,
                                  Long rightPersonId) {
        Ticket ticket = tickets.findByViolationIdAndDeletedAtIsNull(violationId)
                .orElseThrow(() -> new NotFoundException("Appeal of violation", violationId));
        access.requireCanUpdateTicket(actorId, ticket.getId());
        open(ticket.getId());
        if (hseFacts != null && !hseFacts.isBlank()) {
            ticket.setHseFacts(Text.sized("The HSE facts", hseFacts, 10, 1000));
        }
        requireFacts(ticket);
        String decisionNote = note(note);
        Violation violation = violationService.decideAppeal(actorId, violations.require(violationId), decision, rightPersonId);
        finish(actorId, ticket, decision == DisputeStatus.UPHELD ? TicketOutcome.UPHELD : TicketOutcome.OVERTURNED, decisionNote);
        return violation;
    }

    /** The creator withdraws a ticket that is still OPEN (BR-62). An appeal's violation is no longer disputed. */
    public Ticket cancel(Long actorId, Long ticketId) {
        Ticket ticket = tickets.require(ticketId);
        if (!ticket.getCreatedBy().getId().equals(actorId)) {
            throw new AccessDeniedException("Only the person who opened a ticket can cancel it");
        }
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new BusinessRuleException("Only an open ticket can be cancelled");
        }
        if (ticket.getViolation() != null && ticket.getViolation().isDisputed()) {
            ticket.getViolation().setDisputeStatus(DisputeStatus.NONE);
        }
        ticket.cancel(OffsetDateTime.now());
        return tickets.saveAndFlush(ticket);
    }

    /** Kept for the old resolve form: Other tickets resolve as APPROVED. */
    public Ticket resolve(Long actorId, Long ticketId) {
        return resolve(actorId, ticketId, null);
    }

    public Ticket resolve(Long actorId, Long ticketId, String note) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        if (ticket.isDispute()) {
            throw new BusinessRuleException("Resolve a PPE dispute by deciding its violation");
        }
        String outcome = Text.optional("A note", note, 500);
        AppUser resolver = users.require(actorId);
        ticket.decide(ticket.getCategory().getCode() == null ? null : TicketOutcome.APPROVED, outcome, resolver, OffsetDateTime.now());
        if (outcome != null) {
            comments.save(TicketComment.on(ticket, resolver, outcome));
        }
        Ticket saved = tickets.saveAndFlush(ticket);
        tellDecided(saved);
        return saved;
    }

    public TicketComment addComment(Long actorId, Long ticketId, String body) {
        access.requireCanCommentTicket(actorId, ticketId);
        Ticket ticket = tickets.require(ticketId);
        if (!ticket.isUnresolved()) {
            throw new BusinessRuleException("This ticket is already resolved");
        }
        TicketComment comment = comments.save(TicketComment.on(ticket, users.require(actorId),
                Text.required("A comment", body, 1000)));
        Set<Long> told = new LinkedHashSet<>();
        told.add(ticket.getCreatedBy().getId());
        if (ticket.getAssignee() != null) {
            told.add(ticket.getAssignee().getId());
        }
        told.addAll(jdbc.queryForList("SELECT user_id FROM ticket_participant WHERE ticket_id = ?", Long.class, ticketId));
        told.remove(actorId);
        notifications.notifyAll(told, NotificationKind.TICKET_COMMENT,
                Map.of("subject", ticket.getSubject(), "author", comment.getAuthor().getFullName()), link(ticket));
        return comment;
    }

    public void softDelete(Long actorId, Long ticketId) {
        access.requireCanDeleteTicket(actorId, ticketId);
        Ticket ticket = tickets.require(ticketId);
        Violation violation = ticket.getViolation();
        if (violation != null && violation.getDisputeStatus() == DisputeStatus.DISPUTED) {
            throw new BusinessRuleException("Decide the dispute before deleting its ticket");
        }
        ticket.setDeletedAt(OffsetDateTime.now());
    }

    // --- helpers -------------------------------------------------------------------------------------

    private void approveMissingCheckOut(Long actorId, Ticket ticket) {
        CheckIn visit = ticket.getCheckIn();
        Shift shift = visit.getShift();
        if (shift == null) {
            throw new BusinessRuleException("This visit has no shift, so correct it on the Attendance Form");
        }
        locks.requireOpen(visit.getCheckInAt());
        AppUser admin = users.require(actorId);
        CheckInCorrection correction = CheckInCorrection.of(visit, admin, visit.getCheckInAt(), shift.getEndAt(),
                "Missing check-out explained in a ticket");
        correction.setTicket(ticket);
        if (!shift.getEndAt().isAfter(visit.getCheckInAt())) {
            throw new BusinessRuleException("Check-out must be after check-in");
        }
        visit.setCheckOutAt(shift.getEndAt());
        visit.setAutoClosed(false);
        visit.clearIncomplete();
        checkIns.saveAndFlush(visit);
        corrections.save(correction);
    }

    private Ticket finish(Long actorId, Ticket ticket, TicketOutcome outcome, String note) {
        AppUser admin = users.require(actorId);
        ticket.decide(outcome, note, admin, OffsetDateTime.now());
        comments.save(TicketComment.on(ticket, admin, note));
        Ticket saved = tickets.saveAndFlush(ticket);
        tellDecided(saved);
        return saved;
    }

    private void tellDecided(Ticket ticket) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("subject", ticket.getSubject());
        params.put("outcome", ticket.getOutcome() == null ? "RESOLVED" : ticket.getOutcome().name());
        notifications.notify(ticket.getCreatedBy().getId(), NotificationKind.TICKET_DECIDED, params, link(ticket));
    }

    private void requireFacts(Ticket ticket) {
        if (ticket.getHseFacts() == null || ticket.getHseFacts().isBlank()) {
            throw new BusinessRuleException("Write what the HSE Officer of that shift said (10 to 1,000 characters)");
        }
    }

    /** MSG47: a decision needs a note of 10 to 500 characters for the employee. */
    private String note(String note) {
        String text = note == null ? "" : note.strip();
        if (text.length() < 10 || text.length() > 500) {
            throw new BusinessRuleException("Write a note for the employee (10 to 500 characters).");
        }
        return text;
    }

    private Ticket newTicket(Long actorId, TicketCategory category, String subject, String description) {
        Ticket ticket = new Ticket();
        ticket.setCategory(category);
        ticket.setSubject(Text.sized("A subject", subject, 5, 100));
        ticket.setDescription(Text.sized("The details", description, 10, 2000));
        ticket.setCreatedBy(users.require(actorId));
        return ticket;
    }

    private Ticket open(Long ticketId) {
        Ticket ticket = tickets.require(ticketId);
        if (!ticket.isUnresolved()) {
            throw new BusinessRuleException("This ticket is already resolved");
        }
        return ticket;
    }

    private List<Long> activeAdmins() {
        return new java.util.ArrayList<>(jdbc.queryForList("""
                SELECT u.id FROM app_user u JOIN role r ON r.id = u.role_id
                WHERE r.role_name = 'ADMIN' AND u.is_active AND u.deleted_at IS NULL""", Long.class));
    }

    private Specification<Ticket> live() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static String link(Ticket ticket) {
        return "/tickets/" + ticket.getId();
    }
}
