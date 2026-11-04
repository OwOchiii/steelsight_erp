package orochi.steelsight.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.scheduling.model.Zone;
import orochi.steelsight.scheduling.repository.ZoneRepository;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketCategory;
import orochi.steelsight.ticketing.model.TicketComment;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.repository.TicketCategoryRepository;
import orochi.steelsight.ticketing.repository.TicketCommentRepository;
import orochi.steelsight.ticketing.repository.TicketRepository;
import orochi.steelsight.validation.Text;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    static final String PPE_DISPUTE_CATEGORY = "PPE dispute";
    private static final Set<TicketStatus> UNRESOLVED = EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

    private final AccessPolicy access;
    private final TicketRepository tickets;
    private final TicketCategoryRepository categories;
    private final TicketCommentRepository comments;
    private final ViolationRepository violations;
    private final AppUserRepository users;
    private final ZoneRepository zones;

    public record NewTicket(Long categoryId, String subject, String description, Long zoneId, String photoPath) {
    }

    public Ticket create(Long actorId, Long categoryId, String subject, String description) {
        return create(actorId, new NewTicket(categoryId, subject, description, null, null));
    }

    /** Opens an ordinary ticket, such as a Safety concern. Disputes go through {@link #openDispute}. */
    public Ticket create(Long actorId, NewTicket details) {
        access.require(actorId, Permissions.TICKET_CREATE);
        if (details.categoryId() == null) {
            throw new BusinessRuleException("Choose a category");
        }
        TicketCategory category = categories.findById(details.categoryId())
                .orElseThrow(() -> new NotFoundException("Ticket category", details.categoryId()));
        if (PPE_DISPUTE_CATEGORY.equals(category.getCategoryName())) {
            throw new BusinessRuleException("Open a PPE dispute from the violation itself");
        }
        Ticket ticket = newTicket(actorId, category, details.subject(), details.description());
        ticket.setPhotoPath(details.photoPath());
        if (details.zoneId() != null) {
            ticket.setZone(zones.require(details.zoneId()));
        }
        return tickets.saveAndFlush(ticket);
    }

    /** Marks the violation disputed and opens its ticket in one transaction. */
    public Ticket openDispute(Long actorId, Long violationId, String subject, String description) {
        access.require(actorId, Permissions.TICKET_CREATE);
        Violation violation = violations.require(violationId);
        if (!violation.belongsTo(actorId)) {
            throw new AccessDeniedException("You can only dispute your own violation");
        }
        if (violation.getDisputeStatus() != DisputeStatus.NONE) {
            throw new BusinessRuleException("This violation has already been disputed");
        }
        violation.setDisputeStatus(DisputeStatus.DISPUTED);

        TicketCategory category = categories.findByCategoryName(PPE_DISPUTE_CATEGORY)
                .orElseThrow(() -> new NotFoundException("Ticket category", PPE_DISPUTE_CATEGORY));
        Ticket ticket = newTicket(actorId, category, subject, description);
        ticket.setViolation(violation);
        try {
            return tickets.saveAndFlush(ticket);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("This violation already has a dispute ticket");
        }
    }

    /** Categories a user can pick when opening a ticket. A dispute starts from its violation instead. */
    @Transactional(readOnly = true)
    public List<TicketCategory> listCategoriesForNewTicket(Long actorId) {
        access.require(actorId, Permissions.TICKET_CREATE);
        return categories.findAll().stream()
                .filter(category -> !PPE_DISPUTE_CATEGORY.equals(category.getCategoryName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Zone> listZonesForNewTicket(Long actorId) {
        access.require(actorId, Permissions.TICKET_CREATE);
        return zones.findAllByOrderByNameAsc();
    }

    /** The live dispute ticket of a violation, if it has one. */
    @Transactional(readOnly = true)
    public Optional<Ticket> findDispute(Long actorId, Long violationId) {
        Optional<Ticket> dispute = tickets.findByViolationIdAndDeletedAtIsNull(violationId);
        dispute.ifPresent(ticket -> access.requireCanReadTicket(actorId, ticket.getId()));
        return dispute;
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
        access.require(actorId, Permissions.TICKET_READ_OWN);
        return tickets.findAllByCreatedByIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(actorId, page);
    }

    /** Tickets in the categories the actor's role handles. */
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

    /**
     * The tickets this user works on: their role's categories, or every ticket with TICKET_READ_ALL.
     * Unresolved tickets come oldest first, because they have waited longest; resolved ones newest first.
     */
    @Transactional(readOnly = true)
    public Page<Ticket> listQueue(Long actorId, boolean resolved, Pageable page) {
        access.requireAny(actorId, Permissions.TICKET_READ_HANDLED, Permissions.TICKET_READ_ALL);
        Set<TicketStatus> statuses = resolved ? EnumSet.of(TicketStatus.RESOLVED) : UNRESOLVED;
        Sort sort = resolved ? Sort.by("resolvedAt", "id").descending() : Sort.by("createdAt", "id");
        Pageable sorted = PageRequest.of(page.getPageNumber(), page.getPageSize(), sort);
        if (access.has(actorId, Permissions.TICKET_READ_ALL)) {
            return tickets.findAllByStatusInAndDeletedAtIsNull(statuses, sorted);
        }
        return tickets.findAllByCategoryHandlerRoleIdAndStatusInAndDeletedAtIsNull(
                users.require(actorId).getRole().getId(), statuses, sorted);
    }

    @Transactional(readOnly = true)
    public long countUnresolvedInQueue(Long actorId) {
        access.requireAny(actorId, Permissions.TICKET_READ_HANDLED, Permissions.TICKET_READ_ALL);
        if (access.has(actorId, Permissions.TICKET_READ_ALL)) {
            return tickets.countByStatusInAndDeletedAtIsNull(UNRESOLVED);
        }
        return tickets.countByCategoryHandlerRoleIdAndStatusInAndDeletedAtIsNull(
                users.require(actorId).getRole().getId(), UNRESOLVED);
    }

    @Transactional(readOnly = true)
    public boolean canHandle(Long actorId, Long ticketId) {
        return access.canUpdateTicket(actorId, ticketId);
    }

    /** Active people in the role that handles this ticket's category. */
    @Transactional(readOnly = true)
    public List<AppUser> listPossibleAssignees(Long actorId, Long ticketId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        return users.findActiveByRole(tickets.require(ticketId).getCategory().getHandlerRole().getId());
    }

    /** The assignee must also be able to work on this ticket. */
    public Ticket assign(Long actorId, Long ticketId, Long assigneeId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        if (assigneeId == null || !access.canUpdateTicket(assigneeId, ticketId)) {
            throw new BusinessRuleException("That user cannot handle this ticket");
        }
        Ticket ticket = open(ticketId);
        ticket.assignTo(users.require(assigneeId));
        return tickets.saveAndFlush(ticket);
    }

    public Ticket resolve(Long actorId, Long ticketId) {
        return resolve(actorId, ticketId, null);
    }

    /** The note becomes a comment in the same transaction, so the reporter can see the outcome. */
    public Ticket resolve(Long actorId, Long ticketId, String note) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        if (ticket.isDispute()) {
            throw new BusinessRuleException("Resolve a PPE dispute by deciding its violation");
        }
        String outcome = Text.optional("A note", note, Text.LONG);
        AppUser resolver = users.require(actorId);
        ticket.resolveBy(resolver, OffsetDateTime.now());
        if (outcome != null) {
            comments.save(TicketComment.on(ticket, resolver, outcome));
        }
        return tickets.saveAndFlush(ticket);
    }

    public TicketComment addComment(Long actorId, Long ticketId, String body) {
        access.requireCanReadTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        if (!ticket.getCreatedBy().getId().equals(actorId)) {
            access.requireCanUpdateTicket(actorId, ticketId);
        }
        return comments.save(TicketComment.on(ticket, users.require(actorId),
                Text.required("A comment", body, Text.LONG)));
    }

    @Transactional(readOnly = true)
    public List<TicketComment> listComments(Long actorId, Long ticketId) {
        access.requireCanReadTicket(actorId, ticketId);
        return comments.findAllByTicketIdOrderByCreatedAtAsc(ticketId);
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

    private Ticket newTicket(Long actorId, TicketCategory category, String subject, String description) {
        Ticket ticket = new Ticket();
        ticket.setCategory(category);
        ticket.setSubject(Text.required("A subject", subject, Text.SHORT));
        ticket.setDescription(Text.required("A description", description, Text.LONG));
        ticket.setCreatedBy(users.require(actorId));
        return ticket;
    }

    private Ticket open(Long ticketId) {
        Ticket ticket = tickets.require(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new BusinessRuleException("This ticket is already resolved");
        }
        return ticket;
    }
}
