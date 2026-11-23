package orochi.steelsight.scheduling.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.BaseModel;
import orochi.steelsight.model.Versioned;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "shift")
public class Shift extends BaseModel implements Versioned {

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "duration_hours", insertable = false, updatable = false)
    private BigDecimal durationHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status = ShiftStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftKind kind = ShiftKind.REGULAR;

    /** Why the overtime is needed; required for OVERTIME (SRS BR-23, MSG29). */
    @Column
    private String reason;

    public boolean isOvertime() {
        return kind == ShiftKind.OVERTIME;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    public boolean isScheduled() {
        return status == ShiftStatus.SCHEDULED;
    }

    /** Planned or done: every shift that wasn't cancelled. Ended shifts become COMPLETED by the hourly job. */
    public boolean isActive() {
        return status != ShiftStatus.CANCELLED;
    }

    public boolean isUpcoming(OffsetDateTime now) {
        return isScheduled() && endAt.isAfter(now);
    }

    public boolean hasStarted(OffsetDateTime now) {
        return !startAt.isAfter(now);
    }
}
