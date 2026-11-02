package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.BaseModel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "violation")
public class Violation extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ppe_linking_id", nullable = false)
    private PpeLinking ppeLinking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

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
    @Column(name = "dispute_status", nullable = false)
    private DisputeStatus disputeStatus = DisputeStatus.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
}
