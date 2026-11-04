package orochi.steelsight.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketCategory;
import orochi.steelsight.ticketing.model.TicketComment;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.repository.TicketCategoryRepository;
import orochi.steelsight.ticketing.repository.TicketCommentRepository;
import orochi.steelsight.ticketing.repository.TicketRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    static final String PPE_DISPUTE_CATEGORY = "PPE dispute";

    private final AccessPolicy access;
    private final TicketRepository tickets;
    private final TicketCategoryRepository categories;
    private final TicketCommentRepository comments;
    private final ViolationRepository violations;
    private final AppUserRepository users;

    /** Opens an ordinary ticket, such as a Safety concern. Disputes go through {@link #openDispute}. */
    public Ticket create(Long actorId, Long categoryId, String subject, String description) {
        access.require(actorId, Permissions.TICKET_CREATE);
        TicketCategory category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Ticket category", categoryId));
        if (PPE_DISPUTE_CATEGORY.equals(category.getCategoryName())) {
            throw new BusinessRuleException("Open a PPE dispute from the violation itself");
        }
        return tickets.saveAndFlush(newTicket(actorId, category, subject, description));
    }

    /** Marks the violation disputed and opens its ticket in one transaction. */
    public Ticket openDispute(Long actorId, Long violationId, String subject, String description) {
        access.require(actorId, Permissions.TICKET_CREATE);
        Violation violation = violations.findById(violationId)
                .orElseThrow(() -> new NotFoundException("Violation", violationId));
        if (violation.getUser() == null || !violation.getUser().getId().equals(actorId)) {
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

    @Transactional(readOnly = true)
    public Ticket get(Long actorId, Long ticketId) {
        access.requireCanReadTicket(actorId, ticketId);
        return ticket(ticketId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listOwn(Long actorId) {
        access.require(actorId, Permissions.TICKET_READ_OWN);
        return tickets.findAllByCreatedByIdAndDeletedAtIsNull(actorId);
    }

    /** Tickets in the categories the actor's role handles. */
    @Transactional(readOnly = true)
    public List<Ticket> listHandled(Long actorId) {
        access.require(actorId, Permissions.TICKET_READ_HANDLED);
        return tickets.findAllByHandlerRoleId(user(actorId).getRole().getId());
    }

    @Transactional(readOnly = true)
    public List<Ticket> listAll(Long actorId) {
        access.require(actorId, Permissions.TICKET_READ_ALL);
        return tickets.findAllByDeletedAtIsNull();
    }

    /** The assignee must also be able to work on this ticket. */
    public Ticket assign(Long actorId, Long ticketId, Long assigneeId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        if (!access.canUpdateTicket(assigneeId, ticketId)) {
            throw new BusinessRuleException("That user cannot handle this ticket");
        }
        Ticket ticket = open(ticketId);
        ticket.setAssignee(user(assigneeId));
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        return tickets.saveAndFlush(ticket);
    }

    public Ticket resolve(Long actorId, Long ticketId) {
        access.requireCanUpdateTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        if (ticket.getViolation() != null) {
            throw new BusinessRuleException("Resolve a PPE dispute by deciding its violation");
        }
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setResolver(user(actorId));
        ticket.setResolvedAt(OffsetDateTime.now());
        return tickets.saveAndFlush(ticket);
    }

    public TicketComment addComment(Long actorId, Long ticketId, String body) {
        access.requireCanReadTicket(actorId, ticketId);
        Ticket ticket = open(ticketId);
        if (!ticket.getCreatedBy().getId().equals(actorId)) {
            access.requireCanUpdateTicket(actorId, ticketId);
        }
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(user(actorId));
        comment.setBody(body);
        return comments.save(comment);
    }

    @Transactional(readOnly = true)
    public List<TicketComment> listComments(Long actorId, Long ticketId) {
        access.requireCanReadTicket(actorId, ticketId);
        return comments.findAllByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    public void softDelete(Long actorId, Long ticketId) {
        access.requireCanDeleteTicket(actorId, ticketId);
        Ticket ticket = ticket(ticketId);
        Violation violation = ticket.getViolation();
        if (violation != null && violation.getDisputeStatus() == DisputeStatus.DISPUTED) {
            throw new BusinessRuleException("Decide the dispute before deleting its ticket");
        }
        ticket.setDeletedAt(OffsetDateTime.now());
    }

    private Ticket newTicket(Long actorId, TicketCategory category, String subject, String description) {
        Ticket ticket = new Ticket();
        ticket.setCategory(category);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setCreatedBy(user(actorId));
        return ticket;
    }

    private Ticket open(Long ticketId) {
        Ticket ticket = ticket(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new BusinessRuleException("This ticket is already resolved");
        }
        return ticket;
    }

    private Ticket ticket(Long id) {
        return tickets.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("Ticket", id));
    }

    private AppUser user(Long id) {
        return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
