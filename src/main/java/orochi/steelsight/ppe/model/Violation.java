package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.BaseModel;
import orochi.steelsight.model.Versioned;
import orochi.steelsight.scheduling.model.Zone;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One PPE violation. The detection app saves it as PENDING_REVIEW with no person; a person it names
 * becomes the suggestion (V21 trigger). An HSE Officer or the Admin then confirms the person, marks a
 * stranger or dismisses it (SRS BR-48). Only a confirmed violation of a Site employee costs points.
 */
@Getter
@Setter
@Entity
@Table(name = "violation")
public class Violation extends BaseModel implements Versioned {

    @Version
    private Long version;

    /** The confirmed person; null until a decision names an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    /** Set from the camera by the database when the row is saved. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false, insertable = false, updatable = false)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false)
    private ViolationType violationType;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "snapshot_path", nullable = false)
    private String snapshotPath;

    @Column(name = "box_x", precision = 5, scale = 2)
    private BigDecimal boxX;

    @Column(name = "box_y", precision = 5, scale = 2)
    private BigDecimal boxY;

    @Column(name = "box_w", precision = 5, scale = 2)
    private BigDecimal boxW;

    @Column(name = "box_h", precision = 5, scale = 2)
    private BigDecimal boxH;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject")
    private ViolationSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "dismiss_reason")
    private DismissReason dismissReason;

    /** What the officer did on the spot, the visitor's company, or the reason for Other. */
    @Column(name = "review_note")
    private String reviewNote;

    /** The person the camera or the gate suggests (SRS BR-46, BR-47). Never a decision. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_user_id")
    private AppUser suggestedUser;

    @Column(name = "suggestion_similarity", precision = 4, scale = 3)
    private BigDecimal suggestionSimilarity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    /** The officer going to the place; shown to the others, never a lock (SRS 3.2.17 "Take"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taken_by")
    private AppUser takenBy;

    @Column(name = "taken_at")
    private OffsetDateTime takenAt;

    /** The appeal (called a dispute in the code): NONE, DISPUTED, UPHELD or OVERTURNED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false)
    private DisputeStatus disputeStatus = DisputeStatus.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appeal_decided_by")
    private AppUser appealDecidedBy;

    @Column(name = "appeal_decided_at")
    private OffsetDateTime appealDecidedAt;

    public boolean isIdentified() {
        return user != null;
    }

    public boolean isOpen() {
        return reviewStatus.isOpen();
    }

    public boolean isStranger() {
        return subject == ViolationSubject.STRANGER;
    }

    public boolean belongsTo(Long userId) {
        return user != null && user.getId().equals(userId);
    }

    public boolean isDisputed() {
        return disputeStatus == DisputeStatus.DISPUTED;
    }

    /** Counts against the person's score: confirmed, of an employee, and not overturned (SRS BR-57). */
    public boolean counts() {
        return reviewStatus == ReviewStatus.CONFIRMED && user != null && disputeStatus != DisputeStatus.OVERTURNED;
    }

    public void confirm(AppUser person, AppUser reviewer, String note, OffsetDateTime at) {
        reviewStatus = person.isOffice() ? ReviewStatus.REMINDER : ReviewStatus.CONFIRMED;
        subject = ViolationSubject.EMPLOYEE;
        user = person;
        decide(reviewer, note, at);
    }

    public void confirmStranger(AppUser reviewer, String note, OffsetDateTime at) {
        reviewStatus = ReviewStatus.CONFIRMED;
        subject = ViolationSubject.STRANGER;
        user = null;
        decide(reviewer, note, at);
    }

    public void dismiss(DismissReason reason, AppUser reviewer, String note, OffsetDateTime at) {
        reviewStatus = ReviewStatus.DISMISSED;
        dismissReason = reason;
        subject = null;
        user = null;
        decide(reviewer, note, at);
    }

    public void recordAppealDecision(DisputeStatus decision, AppUser decider, OffsetDateTime at) {
        disputeStatus = decision;
        appealDecidedBy = decider;
        appealDecidedAt = at;
    }

    /**
     * An appeal was overturned and the right person is known: the violation goes back to the queue with
     * that person as the suggestion (SRS BR-65). The first person is no longer on it.
     */
    public void reopenFor(AppUser rightPerson) {
        reviewStatus = ReviewStatus.PENDING_REVIEW;
        subject = null;
        user = null;
        suggestedUser = rightPerson;
        suggestionSimilarity = null;
        disputeStatus = DisputeStatus.NONE;
        appealDecidedBy = null;
        appealDecidedAt = null;
        reviewedBy = null;
        reviewedAt = null;
        reviewNote = null;
        takenBy = null;
        takenAt = null;
    }

    private void decide(AppUser reviewer, String note, OffsetDateTime at) {
        reviewedBy = reviewer;
        reviewedAt = at;
        reviewNote = note;
        if (reviewStatus != ReviewStatus.DISMISSED) {
            dismissReason = null;
        }
    }
}
