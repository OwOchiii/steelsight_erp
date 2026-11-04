package orochi.steelsight.ppe.service;

import lombok.RequiredArgsConstructor;
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
import orochi.steelsight.ticketing.model.TicketComment;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.repository.TicketCommentRepository;
import orochi.steelsight.ticketing.repository.TicketRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ViolationService {

    private final AccessPolicy access;
    private final ViolationRepository violations;
    private final AppUserRepository users;
    private final TicketRepository tickets;
    private final TicketCommentRepository comments;

    @Transactional(readOnly = true)
    public Violation get(Long actorId, Long violationId) {
        Violation violation = violation(violationId);
        boolean own = violation.getUser() != null && violation.getUser().getId().equals(actorId);
        access.require(actorId, own ? Permissions.VIOLATION_READ_OWN : Permissions.VIOLATION_READ_ALL);
        return violation;
    }

    @Transactional(readOnly = true)
    public List<Violation> listOwn(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_OWN);
        return violations.findAllByUserIdOrderByDetectedAtDesc(actorId);
    }

    @Transactional(readOnly = true)
    public List<Violation> listAll(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findAll();
    }

    @Transactional(readOnly = true)
    public List<Violation> listDisputed(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findAllByDisputeStatus(DisputeStatus.DISPUTED);
    }

    /** The HSE queue of violations with no known worker. */
    @Transactional(readOnly = true)
    public List<Violation> listUnidentified(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findAllByUserIsNullOrderByDetectedAtDesc();
    }

    public Violation assignWorker(Long actorId, Long violationId, Long workerId) {
        access.require(actorId, Permissions.VIOLATION_REVIEW);
        Violation violation = violation(violationId);
        if (violation.getUser() != null) {
            throw new BusinessRuleException("This violation already has a worker");
        }
        AppUser worker = users.findByIdAndDeletedAtIsNull(workerId)
                .orElseThrow(() -> new NotFoundException("User", workerId));
        violation.setUser(worker);
        return violations.saveAndFlush(violation);
    }

    /**
     * Decides a dispute and resolves its ticket in one transaction, so neither can happen without
     * the other. If two officers decide at once, the version column makes the second one fail.
     */
    public Violation decide(Long actorId, Long violationId, DisputeStatus decision, String note) {
        access.require(actorId, Permissions.VIOLATION_REVIEW);
        if (decision != DisputeStatus.UPHELD && decision != DisputeStatus.OVERTURNED) {
            throw new BusinessRuleException("A decision must be UPHELD or OVERTURNED");
        }
        Violation violation = violation(violationId);
        if (violation.getDisputeStatus() != DisputeStatus.DISPUTED) {
            throw new BusinessRuleException("Only a disputed violation can be decided");
        }
        if (actorId.equals(violation.getUser().getId())) {
            throw new AccessDeniedException("You cannot decide a dispute about your own violation");
        }
        AppUser reviewer = users.findByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new NotFoundException("User", actorId));
        OffsetDateTime now = OffsetDateTime.now();
        violation.setDisputeStatus(decision);
        violation.setReviewedBy(reviewer);
        violation.setReviewedAt(now);

        tickets.findByViolationIdAndDeletedAtIsNull(violationId).ifPresent(ticket -> {
            ticket.setStatus(TicketStatus.RESOLVED);
            ticket.setResolver(reviewer);
            ticket.setResolvedAt(now);
            if (note != null && !note.isBlank()) {
                addComment(ticket, reviewer, note);
            }
        });
        return violations.saveAndFlush(violation);
    }

    private void addComment(Ticket ticket, AppUser author, String body) {
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(body);
        comments.save(comment);
    }

    private Violation violation(Long id) {
        return violations.findById(id).orElseThrow(() -> new NotFoundException("Violation", id));
    }
}
