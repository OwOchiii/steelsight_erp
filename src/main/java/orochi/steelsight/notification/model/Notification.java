package orochi.steelsight.notification.model;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.ColumnTransformer;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.IdentifiedModel;

import java.time.OffsetDateTime;

/**
 * Something a person should know. The words aren't stored, only the kind and its parameters, so each
 * page shows it in the reader's language and the Zalo copy is written in Vietnamese (SRS 3.1.5 #13).
 * Every notice is saved here first; Zalo is an extra channel (BR-15).
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

    /** A JSON object of the parameters, read with NoticeText. */
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String params;

    /** The page the notice opens, such as /violations/12. */
    @Column(updatable = false)
    private String link;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    public static Notification of(AppUser user, NotificationKind kind, String paramsJson, String link) {
        Notification notification = new Notification();
        notification.user = user;
        notification.kind = kind;
        notification.params = paramsJson;
        notification.link = link;
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
