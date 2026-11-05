package orochi.steelsight.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.IdentifiedModel;

import java.time.OffsetDateTime;

/**
 * Something a person should know. The words aren't stored: a page shows them in the reader's language.
 * Safety notices are written by a database trigger, because detection writes violations directly.
 */
@Getter
@Entity
@Table(name = "notification")
public class Notification extends IdentifiedModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationKind kind;

    /** The score when a safety notice was sent. */
    @Column(updatable = false)
    private Integer score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "safety_alert_id", updatable = false)
    private SafetyAlert safetyAlert;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Notification hseAlert(AppUser officer, SafetyAlert alert, OffsetDateTime at) {
        Notification notification = new Notification();
        notification.user = officer;
        notification.kind = NotificationKind.HSE_VIOLATION_ALERT;
        notification.safetyAlert = alert;
        notification.createdAt = at;
        return notification;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(OffsetDateTime at) {
        if (readAt == null) {
            readAt = at;
        }
    }
}
