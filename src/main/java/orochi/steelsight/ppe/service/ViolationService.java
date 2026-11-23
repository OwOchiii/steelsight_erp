package orochi.steelsight.ppe.service;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.calendar.MonthLocks;
import orochi.steelsight.calendar.SiteCalendar;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.StaffGroup;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.notification.model.NotificationKind;
import orochi.steelsight.notification.service.NotificationService;
import orochi.steelsight.ppe.model.DismissReason;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.ReviewStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.model.ViolationSubject;
import orochi.steelsight.ppe.model.ViolationType;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.validation.Text;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Violation review (SRS UC-11, workflow 1.2.7, BR-45 to BR-55). Every violation arrives PENDING_REVIEW
 * with no person. An HSE Officer or the Admin confirms who it was, marks a stranger, or dismisses it.
 * In a work zone the person list starts with the zone's team (the team's decision on zones); at the
 * gate the suggestion is whoever CAM-01 recorded within 30 seconds (BR-46).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ViolationService {

    static final Duration GATE_WINDOW = Duration.ofSeconds(30);
    static final Set<ReviewStatus> OWN_STATUSES = EnumSet.of(ReviewStatus.CONFIRMED, ReviewStatus.REMINDER);
    static final Set<ReviewStatus> OPEN = EnumSet.of(ReviewStatus.PENDING_REVIEW, ReviewStatus.OVERDUE);

    private final AccessPolicy access;
    private final ViolationRepository violations;
    private final AppUserRepository users;
    private final NotificationService notifications;
    private final SafetyScoreService scores;
    private final MonthLocks locks;
    private final SiteCalendar calendar;
    private final JdbcTemplate jdbc;

    /** Where a person comes in the review's person list (BR-49 and the zone's team). */
    public enum CandidateGroup { SUGGESTED, ZONE_TEAM, ON_SITE, EVERYONE }

    public record Candidate(AppUser person, CandidateGroup group) {
    }

    /** A filter of the Violation List (SRS 3.2.18). status may also be "STRANGER". */
    public record ListFilter(YearMonth month, Long zoneId, String status, String person) {
    }

    /** The counts at the top of the Violation List: by zone name and by missing item. */
    public record MonthCounts(Map<String, Long> byZone, Map<ViolationType, Long> byType) {
    }

    // --- reading -------------------------------------------------------------------------------------

    /**
     * HSE and the Admin see every violation. Anyone else sees only their own, once a decision put it on
     * them; pending and dismissed ones are not shown to them (SRS 3.2.19).
     */
    @Transactional(readOnly = true)
    public Violation get(Long actorId, Long violationId) {
        Violation violation = violations.require(violationId);
        if (!access.has(actorId, Permissions.VIOLATION_READ_ALL)) {
            access.require(actorId, Permissions.VIOLATION_READ_OWN);
            if (!violation.belongsTo(actorId) || !OWN_STATUSES.contains(violation.getReviewStatus())) {
                throw new AccessDeniedException("This is someone else's violation");
            }
        }
        return violation;
    }

    @Transactional(readOnly = true)
    public List<Violation> listOwn(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_OWN);
        return violations.findOwn(actorId, OWN_STATUSES);
    }

    @Transactional(readOnly = true)
    public Page<Violation> listOwn(Long actorId, YearMonth month, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_OWN);
        return violations.findOwnInPeriod(actorId, OWN_STATUSES, calendar.startOf(month),
                calendar.startOf(month.plusMonths(1)), page);
    }

    /** One worker's violations since a date, for anyone who can look at every worker. */
    @Transactional(readOnly = true)
    public List<Violation> listOfWorkerSince(Long actorId, Long workerId, OffsetDateTime from) {
        access.require(actorId, Permissions.USER_READ_ALL);
        return violations.findAllByUserIdAndDetectedAtGreaterThanEqualOrderByDetectedAtDescIdDesc(workerId, from);
    }

    /** SRS 3.2.16: the violations still waiting for a decision, oldest first. day is null for every day. */
    @Transactional(readOnly = true)
    public Page<Violation> queue(Long actorId, Long zoneId, LocalDate day, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        Specification<Violation> open = (root, query, cb) -> root.get("reviewStatus").in(OPEN);
        Specification<Violation> spec = open.and(inZone(zoneId));
        if (day != null) {
            spec = spec.and(between(calendar.startOfDay(day), calendar.startOfDay(day.plusDays(1))));
        }
        return violations.findAll(spec, sorted(page, Sort.by("detectedAt", "id")));
    }

    @Transactional(readOnly = true)
    public Page<Violation> listDisputed(Long actorId, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findAllByDisputeStatusOrderByDetectedAtAscIdAsc(DisputeStatus.DISPUTED, page);
    }

    /** SRS 3.2.18: every violation of a month with filters, newest first. */
    @Transactional(readOnly = true)
    public Page<Violation> list(Long actorId, ListFilter filter, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        Specification<Violation> spec = inMonth(filter.month()).and(inZone(filter.zoneId()));
        if (filter.status() != null && !filter.status().isBlank()) {
            String status = filter.status();
            spec = spec.and((root, query, cb) -> status.equals("STRANGER")
                    ? cb.equal(root.get("subject"), ViolationSubject.STRANGER)
                    : cb.equal(root.get("reviewStatus"), ReviewStatus.valueOf(status)));
        }
        if (filter.person() != null && !filter.person().isBlank()) {
            String like = "%" + filter.person().strip().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> {
                var person = root.join("user", JoinType.LEFT);
                return cb.or(cb.like(cb.lower(person.get("fullName")), like, '\\'),
                        cb.like(cb.lower(person.get("employeeCode")), like, '\\'));
            });
        }
        return violations.findAll(spec, sorted(page, Sort.by("detectedAt", "id").descending()));
    }

    /** Violations that weren't dismissed or overturned, counted by zone and by missing item. */
    @Transactional(readOnly = true)
    public MonthCounts countMonth(Long actorId, YearMonth month) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        Map<String, Long> byZone = new TreeMap<>();
        Map<ViolationType, Long> byType = new EnumMap<>(ViolationType.class);
        jdbc.query("""
                SELECT z.name, v.violation_type, count(*) FROM violation v JOIN zone z ON z.id = v.zone_id
                WHERE v.detected_at >= ? AND v.detected_at < ? AND v.review_status <> 'DISMISSED'
                  AND v.dispute_status <> 'OVERTURNED'
                GROUP BY z.name, v.violation_type""", row -> {
            byZone.merge(row.getString(1), row.getLong(3), Long::sum);
            byType.merge(ViolationType.valueOf(row.getString(2)), row.getLong(3), Long::sum);
        }, calendar.startOf(month), calendar.startOf(month.plusMonths(1)));
        return new MonthCounts(byZone, byType);
    }

    @Transactional(readOnly = true)
    public List<Violation> listSince(Long actorId, OffsetDateTime from) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findDetectedSince(from);
    }

    @Transactional(readOnly = true)
    public long countOpen(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.countByReviewStatusIn(OPEN);
    }

    @Transactional(readOnly = true)
    public long countOverdue(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.countByReviewStatus(ReviewStatus.OVERDUE);
    }

    @Transactional(readOnly = true)
    public long countDisputed(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.countByDisputeStatus(DisputeStatus.DISPUTED);
    }

    // --- suggestion and person list ------------------------------------------------------------------

    /**
     * The suggested person: the one the camera named, or at the gate the only employee CAM-01 recorded
     * within 30 seconds of the violation, walking in or out (BR-46). Never a decision.
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> suggestion(Violation violation) {
        if (violation.getSuggestedUser() != null) {
            return Optional.of(violation.getSuggestedUser());
        }
        if (!violation.getZone().isGatePpe()) {
            return Optional.empty();
        }
        OffsetDateTime at = violation.getDetectedAt();
        List<Long> people = jdbc.queryForList("""
                SELECT DISTINCT c.user_id FROM check_in c
                WHERE c.method = 'FACE'
                  AND (c.check_in_at BETWEEN ? AND ?
                       OR (c.check_out_at BETWEEN ? AND ? AND NOT c.auto_closed))""", Long.class,
                at.minus(GATE_WINDOW), at.plus(GATE_WINDOW), at.minus(GATE_WINDOW), at.plus(GATE_WINDOW));
        return people.size() == 1 ? users.findById(people.get(0)) : Optional.empty();
    }

    /**
     * The person list of the review, in order: the suggestion; the members of the zone's team who had a
     * shift at that time, then the rest of that team; the people on site at that time; then everyone
     * active (BR-49, and the team's decision that a work zone belongs to a team).
     */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(Long actorId, Violation violation) {
        access.require(actorId, Permissions.VIOLATION_REVIEW);
        Map<Long, Candidate> ordered = new LinkedHashMap<>();
        suggestion(violation).ifPresent(person -> ordered.put(person.getId(), new Candidate(person, CandidateGroup.SUGGESTED)));
        OffsetDateTime at = violation.getDetectedAt();
        if (violation.getZone().getTeam() != null) {
            Long team = violation.getZone().getTeam().getId();
            List<Long> teamIds = new ArrayList<>(jdbc.queryForList("""
                    SELECT u.id FROM app_user u
                    WHERE u.team_id = ? AND u.deleted_at IS NULL AND u.is_active
                    ORDER BY EXISTS (SELECT 1 FROM shift s WHERE s.user_id = u.id AND s.status <> 'CANCELLED'
                                       AND ? >= s.start_at AND ? < s.end_at) DESC, u.full_name, u.id""",
                    Long.class, team, at, at));
            addAll(ordered, teamIds, CandidateGroup.ZONE_TEAM);
        }
        addAll(ordered, jdbc.queryForList("""
                SELECT u.id FROM check_in c JOIN app_user u ON u.id = c.user_id
                WHERE c.check_in_at <= ? AND (c.check_out_at IS NULL OR c.check_out_at > ?) AND u.deleted_at IS NULL
                ORDER BY u.full_name, u.id""", Long.class, at, at), CandidateGroup.ON_SITE);
        users.findAllActiveByName().forEach(person ->
                ordered.putIfAbsent(person.getId(), new Candidate(person, CandidateGroup.EVERYONE)));
        return List.copyOf(ordered.values());
    }

    private void addAll(Map<Long, Candidate> ordered, List<Long> ids, CandidateGroup group) {
        Map<Long, AppUser> found = new HashMap<>();
        users.findAllById(new LinkedHashSet<>(ids)).forEach(person -> found.put(person.getId(), person));
        for (Long id : ids) {
            AppUser person = found.get(id);
            if (person != null) {
                ordered.putIfAbsent(id, new Candidate(person, group));
            }
        }
    }

    // --- decisions -----------------------------------------------------------------------------------

    /**
     * Confirms who broke the rule, or that it was a stranger (SRS 3.2.17). A Site employee loses 5 points
     * and is told the new score, level and appeal deadline; an Office employee gets a reminder (BR-51).
     */
    public Violation confirm(Long actorId, Long violationId, Long personId, boolean stranger, String note,
                             long expectedVersion) {
        Violation violation = forDecision(actorId, violationId, expectedVersion);
        String cleanNote = Text.optional("A note", note, Text.LONG);
        AppUser reviewer = users.require(actorId);
        OffsetDateTime now = OffsetDateTime.now();
        if (stranger) {
            violation.confirmStranger(reviewer, cleanNote, now);
            return violations.saveAndFlush(violation);
        }
        if (personId == null) {
            throw new BusinessRuleException("Choose the person, or mark the violation as Stranger.");
        }
        AppUser person = users.require(personId);
        if (person.getDeletedAt() != null) {
            throw new BusinessRuleException("Choose the person, or mark the violation as Stranger.");
        }
        SafetyScoreService.Score before = scores.current(person.getId());
        violation.confirm(person, reviewer, cleanNote, now);
        Violation saved = violations.saveAndFlush(violation);
        tellPerson(saved, before);
        return saved;
    }

    /** SRS BR-50: a reason from the list; Other needs 10 to 500 characters. */
    public Violation dismiss(Long actorId, Long violationId, DismissReason reason, String otherReason,
                             long expectedVersion) {
        Violation violation = forDecision(actorId, violationId, expectedVersion);
        String written = otherReason == null ? "" : otherReason.strip();
        if (reason == null || (reason == DismissReason.OTHER && (written.length() < 10 || written.length() > 500))) {
            throw new BusinessRuleException("Choose the reason. For Other, write the reason (10 to 500 characters).");
        }
        violation.dismiss(reason, users.require(actorId), reason == DismissReason.OTHER ? written : null, OffsetDateTime.now());
        return violations.saveAndFlush(violation);
    }

    /** Shows the other officers that this one is going to the place. Not a lock (SRS 3.2.17 "Take"). */
    public Violation take(Long actorId, Long violationId) {
        access.require(actorId, Permissions.VIOLATION_REVIEW);
        Violation violation = violations.require(violationId);
        if (!violation.isOpen()) {
            throw alreadyDecided(violation);
        }
        violation.setTakenBy(users.require(actorId));
        violation.setTakenAt(OffsetDateTime.now());
        return violations.saveAndFlush(violation);
    }

    private Violation forDecision(Long actorId, Long violationId, long expectedVersion) {
        access.require(actorId, Permissions.VIOLATION_REVIEW);
        Violation violation = violations.require(violationId);
        if (!violation.isOpen()) {
            throw alreadyDecided(violation);
        }
        locks.requireOpen(violation.getDetectedAt());
        violation.requireVersion(expectedVersion);
        return violation;
    }

    /** MSG37: another officer decided first; the reloaded page shows who and when. */
    private BusinessRuleException alreadyDecided(Violation violation) {
        return new BusinessRuleException("This violation was already decided by another officer. Reload the page to see the decision.");
    }

    private void tellPerson(Violation violation, SafetyScoreService.Score before) {
        AppUser person = violation.getUser();
        Map<String, Object> params = new HashMap<>();
        params.put("type", violation.getViolationType().name());
        params.put("zone", violation.getZone() == null ? "" : violation.getZone().getName());
        params.put("detectedAt", violation.getDetectedAt().toString());
        String link = "/violations/" + violation.getId();
        if (violation.getReviewStatus() == ReviewStatus.REMINDER) {
            notifications.notify(person.getId(), NotificationKind.REMINDER, params, link);
            return;
        }
        SafetyScoreService.Score after = scores.current(person.getId());
        if (after != null) {
            params.put("score", after.value());
            params.put("level", after.level().number());
        }
        params.put("appealUntil", appealDeadline(violation).toString());
        notifications.notify(person.getId(), NotificationKind.VIOLATION_CONFIRMED, params, link);
        if (before != null && after != null && after.level().compareTo(before.level()) > 0) {
            notifications.notify(person.getId(), NotificationKind.LEVEL_WORSE,
                    Map.of("level", after.level().number(), "reduction", after.level().reductionPercent()), "/violations");
        }
    }

    // --- appeals -------------------------------------------------------------------------------------

    /** BR-64: an appeal is allowed within 3 working days of the confirmation. */
    public OffsetDateTime appealDeadline(Violation violation) {
        OffsetDateTime confirmed = violation.getReviewedAt() == null ? violation.getDetectedAt() : violation.getReviewedAt();
        return calendar.deadline(confirmed, appealWorkingDays());
    }

    private int appealWorkingDays() {
        Integer days = jdbc.queryForObject("SELECT appeal_working_days FROM company_setting", Integer.class);
        return days == null ? 3 : days;
    }

    /** Null when an appeal is allowed; otherwise the reason it isn't, in the words of the SRS. */
    @Transactional(readOnly = true)
    public String whyNoAppeal(Long actorId, Violation violation) {
        if (!violation.belongsTo(actorId)) {
            return "You can only appeal your own violation";
        }
        if (violation.getReviewStatus() == ReviewStatus.REMINDER) {
            return "A reminder does not change your score and cannot be appealed.";
        }
        if (violation.getReviewStatus() != ReviewStatus.CONFIRMED || violation.getUser().getStaffGroup() != StaffGroup.SITE) {
            return "Only a confirmed violation can be appealed";
        }
        if (violation.getDisputeStatus() != DisputeStatus.NONE) {
            return "This violation has already been appealed.";
        }
        if (locks.isLocked(violation.getDetectedAt())) {
            return "The month is locked. No change is possible.";
        }
        if (OffsetDateTime.now().isAfter(appealDeadline(violation))) {
            return "The appeal period has ended.";
        }
        return null;
    }

    /** Marks the violation appealed; the ticket is opened in the same transaction by TicketService. */
    public void markAppealed(Long actorId, Violation violation) {
        String refused = whyNoAppeal(actorId, violation);
        if (refused != null) {
            throw new BusinessRuleException(refused);
        }
        violation.setDisputeStatus(DisputeStatus.DISPUTED);
    }

    /**
     * Records the Admin's decision on an appeal (BR-65). UPHELD keeps the points; OVERTURNED gives them
     * back, and if the right person is known the violation goes back to the queue for that person.
     * The ticket is resolved by the caller in the same transaction.
     */
    public Violation decideAppeal(Long actorId, Violation violation, DisputeStatus decision, Long rightPersonId) {
        if (decision != DisputeStatus.UPHELD && decision != DisputeStatus.OVERTURNED) {
            throw new BusinessRuleException("A decision must be UPHELD or OVERTURNED");
        }
        if (!violation.isDisputed()) {
            throw new BusinessRuleException("Only a disputed violation can be decided");
        }
        if (violation.belongsTo(actorId)) {
            throw new AccessDeniedException("You cannot decide your own request. Another Admin will handle it.");
        }
        locks.requireOpen(violation.getDetectedAt());
        AppUser decider = users.require(actorId);
        if (decision == DisputeStatus.OVERTURNED && rightPersonId != null) {
            violation.reopenFor(users.require(rightPersonId));
        } else {
            violation.recordAppealDecision(decision, decider, OffsetDateTime.now());
        }
        return violations.saveAndFlush(violation);
    }

    private static Pageable sorted(Pageable page, Sort sort) {
        return page.isUnpaged() ? Pageable.unpaged(sort) : PageRequest.of(page.getPageNumber(), page.getPageSize(), sort);
    }

    // --- specifications ------------------------------------------------------------------------------

    private Specification<Violation> inZone(Long zoneId) {
        return (root, query, cb) -> zoneId == null ? cb.conjunction() : cb.equal(root.get("zone").get("id"), zoneId);
    }

    private Specification<Violation> inMonth(YearMonth month) {
        return between(calendar.startOf(month), calendar.startOf(month.plusMonths(1)));
    }

    private Specification<Violation> between(OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> cb.and(cb.greaterThanOrEqualTo(root.get("detectedAt"), from),
                cb.lessThan(root.get("detectedAt"), to));
    }
}
