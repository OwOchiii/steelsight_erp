package orochi.steelsight.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import orochi.steelsight.model.IdentifiedModel;

import java.time.OffsetDateTime;

/** A report from the detection software that too many violations are happening. */
@Getter
@Entity
@Table(name = "safety_alert")
public class SafetyAlert extends IdentifiedModel {

    @Column(name = "event_id", nullable = false, updatable = false)
    private java.util.UUID eventId;

    @Column(name = "violation_count", nullable = false, updatable = false)
    private int violationCount;

    @Column(name = "window_seconds", nullable = false, updatable = false)
    private int windowSeconds;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "camera_code", updatable = false)
    private String cameraCode;

    @Column(nullable = false, updatable = false)
    private boolean notified;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;
}
