package orochi.steelsight.attendance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.IdentifiedModel;

import java.time.OffsetDateTime;
import java.util.Objects;

/** What one hand correction of a visit changed. Only ever added to. */
@Getter
@Entity
@Table(name = "check_in_correction")
public class CheckInCorrection extends IdentifiedModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "check_in_id", nullable = false, updatable = false)
    private CheckIn checkIn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by", nullable = false, updatable = false)
    private AppUser correctedBy;

    @Column(name = "corrected_at", nullable = false, updatable = false)
    private OffsetDateTime correctedAt;

    @Column(name = "old_check_in_at", nullable = false, updatable = false)
    private OffsetDateTime oldCheckInAt;

    @Column(name = "old_check_out_at", updatable = false)
    private OffsetDateTime oldCheckOutAt;

    @Column(name = "old_note", updatable = false)
    private String oldNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_recorded_by", updatable = false)
    private AppUser oldRecordedBy;

    @Column(name = "old_auto_closed", nullable = false, updatable = false)
    private boolean oldAutoClosed;

    @Column(name = "new_check_in_at", nullable = false, updatable = false)
    private OffsetDateTime newCheckInAt;

    @Column(name = "new_check_out_at", updatable = false)
    private OffsetDateTime newCheckOutAt;

    @Column(name = "new_note", updatable = false)
    private String newNote;

    protected CheckInCorrection() {
    }

    public boolean isCheckInChanged() {
        return !oldCheckInAt.isEqual(newCheckInAt);
    }

    public boolean isCheckOutChanged() {
        return oldCheckOutAt == null || newCheckOutAt == null
                ? oldCheckOutAt != newCheckOutAt
                : !oldCheckOutAt.isEqual(newCheckOutAt);
    }

    public boolean isNoteChanged() {
        return !Objects.equals(oldNote, newNote);
    }

    /** Call this before changing the visit, so that it still holds the old values. */
    public static CheckInCorrection of(CheckIn visit, AppUser correctedBy, OffsetDateTime newCheckInAt,
                                       OffsetDateTime newCheckOutAt, String newNote) {
        CheckInCorrection correction = new CheckInCorrection();
        correction.checkIn = visit;
        correction.correctedBy = correctedBy;
        correction.correctedAt = OffsetDateTime.now();
        correction.oldCheckInAt = visit.getCheckInAt();
        correction.oldCheckOutAt = visit.getCheckOutAt();
        correction.oldNote = visit.getNote();
        correction.oldRecordedBy = visit.getRecordedBy();
        correction.oldAutoClosed = visit.isAutoClosed();
        correction.newCheckInAt = newCheckInAt;
        correction.newCheckOutAt = newCheckOutAt;
        correction.newNote = newNote;
        return correction;
    }
}
