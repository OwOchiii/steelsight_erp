package orochi.steelsight.ticketing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.SoftDeletableModel;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.scheduling.model.Zone;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "ticket")
public class Ticket extends SoftDeletableModel {

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TicketCategory category;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolver_id")
    private AppUser resolver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "violation_id")
    private Violation violation;

    /** Where the problem is, such as the location of a hazard. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /** Path of a photo in the private storage bucket. */
    @Column(name = "photo_path")
    private String photoPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private TicketOutcome outcome;

    /** Shown to the creator with the decision (SRS 3.2.22, 10 to 500 characters). */
    @Column(name = "decision_note")
    private String decisionNote;

    /** What the HSE Officer of that shift said, written by the Admin. */
    @Column(name = "hse_facts")
    private String hseFacts;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    /** Attendance error, missing check-out and leave request: the shift it is about. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private orochi.steelsight.scheduling.model.Shift shift;

    /** Missing check-out: the visit flagged Incomplete. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_id")
    private orochi.steelsight.attendance.model.CheckIn checkIn;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction")
    private Direction direction;

    @Column(name = "gate_time")
    private OffsetDateTime gateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type")
    private LeaveType leaveType;

    @Column(name = "leave_from")
    private OffsetDateTime leaveFrom;

    @Column(name = "leave_to")
    private OffsetDateTime leaveTo;

    public boolean isCancelled() {
        return status == TicketStatus.CANCELLED;
    }

    public boolean isUnresolved() {
        return status.isUnresolved();
    }

    public boolean isAppeal() {
        return category.is(CategoryCode.PPE_APPEAL);
    }

    public void decide(TicketOutcome decided, String note, AppUser by, OffsetDateTime at) {
        outcome = decided;
        decisionNote = note;
        resolveBy(by, at);
    }

    public void cancel(OffsetDateTime at) {
        status = TicketStatus.CANCELLED;
        cancelledAt = at;
    }

    public boolean isResolved() {
        return status == TicketStatus.RESOLVED;
    }

    /** A PPE dispute is resolved by deciding its violation. */
    public boolean isDispute() {
        return violation != null;
    }

    public void assignTo(AppUser user) {
        assignee = user;
        if (status == TicketStatus.OPEN) {
            status = TicketStatus.IN_PROGRESS;
        }
    }

    public void resolveBy(AppUser user, OffsetDateTime at) {
        status = TicketStatus.RESOLVED;
        resolver = user;
        resolvedAt = at;
    }
}
