package orochi.steelsight.attendance.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.BaseModel;
import orochi.steelsight.ppe.model.Camera;
import orochi.steelsight.ppe.model.PpeLinking;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.Zone;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One visit to site: check-in, then check-out. */
@Getter
@Setter
@Entity
@Table(name = "check_in")
public class CheckIn extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id")
    private Camera camera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ppe_linking_id")
    private PpeLinking ppeLinking;

    @Column(name = "check_in_at", nullable = false)
    private OffsetDateTime checkInAt = OffsetDateTime.now();

    /** Null means the worker is still on site. */
    @Column(name = "check_out_at")
    private OffsetDateTime checkOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckInMethod method = CheckInMethod.FACE;

    @Column(name = "face_similarity", precision = 5, scale = 2)
    private BigDecimal faceSimilarity;

    @Column(name = "snapshot_path")
    private String snapshotPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private AppUser recordedBy;

    private String note;

    /** True when check_out_at is an estimate because the worker never checked out. */
    @Column(name = "auto_closed", nullable = false)
    private boolean autoClosed = false;
}
