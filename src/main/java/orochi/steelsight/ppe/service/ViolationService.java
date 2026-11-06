package orochi.steelsight.ppe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.ticketing.model.TicketComment;
import orochi.steelsight.ticketing.repository.TicketCommentRepository;
import orochi.steelsight.ticketing.repository.TicketRepository;
import orochi.steelsight.validation.Text;

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

    /** An HSE officer can be caught by a camera too, and reads that violation through VIOLATION_READ_ALL. */
    @Transactional(readOnly = true)
    public Violation get(Long actorId, Long violationId) {
        Violation violation = violations.require(violationId);
        if (!access.has(actorId, Permissions.VIOLATION_READ_ALL)) {
            access.require(actorId, Permissions.VIOLATION_READ_OWN);
            if (!violation.belongsTo(actorId)) {
                throw new AccessDeniedException("This is someone else's violation");
            }
        }
        return violation;
    }

    @Transactional(readOnly = true)
    public List<Violation> listOwn(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_OWN);
        return violations.findAllByUserIdOrderByDetectedAtDesc(actorId);
    }

    @Transactional(readOnly = true)
    public Page<Violation> listOwn(Long actorId, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_OWN);
        return violations.findAllByUserIdOrderByDetectedAtDescIdDesc(actorId, page);
    }

    /** One worker's violations since a date, for anyone who can look at every worker. */
    @Transactional(readOnly = true)
    public List<Violation> listOfWorkerSince(Long actorId, Long workerId, OffsetDateTime from) {
        access.require(actorId, Permissions.USER_READ_ALL);
        return violations.findAllByUserIdAndDetectedAtGreaterThanEqualOrderByDetectedAtDescIdDesc(workerId, from);
    }

    @Transactional(readOnly = true)
    public Page<Violation> listAll(Long actorId, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findAllByOrderByDetectedAtDescIdDesc(page);
    }

    @Transactional(readOnly = true)
    public Page<Violation> listDisputed(Long actorId, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findAllByDisputeStatusOrderByDetectedAtAscIdAsc(DisputeStatus.DISPUTED, page);
    }

    @Transactional(readOnly = true)
    public List<Violation> listSince(Long actorId, OffsetDateTime from) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findDetectedSince(from);
    }

    /** The HSE queue of violations with no known worker. */
    @Transactional(readOnly = true)
    public Page<Violation> listUnidentified(Long actorId, Pageable page) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.findAllByUserIsNullOrderByDetectedAtDescIdDesc(page);
    }

    @Transactional(readOnly = true)
    public long countDisputed(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.countByDisputeStatus(DisputeStatus.DISPUTED);
    }

    @Transactional(readOnly = true)
    public long countUnidentified(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return violations.countByUserIsNull();
    }

    public Violation assignWorker(Long actorId, Long violationId, Long workerId) {
        access.require(actorId, Permissions.VIOLATION_REVIEW);
        if (workerId == null) {
            throw new BusinessRuleException("Choose a worker");
        }
        Violation violation = violations.require(violationId);
        if (violation.isIdentified()) {
            throw new BusinessRuleException("This violation already has a worker");
        }
        violation.setUser(users.require(workerId));
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
        Violation violation = violations.require(violationId);
        if (!violation.isDisputed()) {
            throw new BusinessRuleException("Only a disputed violation can be decided");
        }
        if (violation.belongsTo(actorId)) {
            throw new AccessDeniedException("You cannot decide a dispute about your own violation");
        }
        String reason = Text.optional("A note", note, Text.LONG);
        AppUser reviewer = users.require(actorId);
        OffsetDateTime now = OffsetDateTime.now();
        violation.recordDecision(decision, reviewer, now);

        tickets.findByViolationIdAndDeletedAtIsNull(violationId).ifPresent(ticket -> {
            ticket.resolveBy(reviewer, now);
            if (reason != null) {
                comments.save(TicketComment.on(ticket, reviewer, reason));
            }
        });
        return violations.saveAndFlush(violation);
    }
}
