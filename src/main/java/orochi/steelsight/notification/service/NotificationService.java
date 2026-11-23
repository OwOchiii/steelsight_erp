package orochi.steelsight.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.notification.model.Notification;
import orochi.steelsight.notification.model.NotificationKind;
import orochi.steelsight.notification.repository.NotificationRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Saves notices and lets each person read their own. A notice is saved in the same transaction as the
 * change it reports, so a failed save tells nobody; the database adds its Zalo delivery row (V21), and
 * ZaloDispatcher sends it after the commit (SRS BR-15, BR-16).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final AppUserRepository users;
    private final JdbcTemplate jdbc;

    /** One delivery result per notice, as shown on the Notifications page. */
    public record Delivery(String status, int attempts, String lastError) {
    }

    public Notification notify(Long userId, NotificationKind kind, Map<String, ?> params, String link) {
        return notifications.save(Notification.of(users.getReferenceById(userId), kind, NoticeText.json(params), link));
    }

    public void notifyAll(Collection<Long> userIds, NotificationKind kind, Map<String, ?> params, String link) {
        userIds.stream().distinct().forEach(id -> notify(id, kind, params, link));
    }

    @Transactional(readOnly = true)
    public Page<Notification> listOwn(Long actorId, Pageable page) {
        return notifications.findAllByUserIdOrderByCreatedAtDescIdDesc(actorId, page);
    }

    @Transactional(readOnly = true)
    public Map<Long, Delivery> deliveries(List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Delivery> found = new java.util.HashMap<>();
        jdbc.query("SELECT notification_id, status, attempts, last_error FROM notification_delivery WHERE notification_id = ANY (?)",
                row -> {
                    found.put(row.getLong(1), new Delivery(row.getString(2), row.getInt(3), row.getString(4)));
                }, (Object) notificationIds.toArray(Long[]::new));
        return found;
    }

    @Transactional(readOnly = true)
    public long countUnread(Long actorId) {
        return notifications.countByUserIdAndReadAtIsNull(actorId);
    }

    /** For the Admin's Home: Zalo messages that gave up (SRS 3.2.3). */
    @Transactional(readOnly = true)
    public long countFailedDeliveries() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE status = 'FAILED'", Long.class);
        return count == null ? 0 : count;
    }

    public void markRead(Long actorId, Long notificationId) {
        Notification notification = notifications.require(notificationId);
        if (!notification.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("This is someone else's notification");
        }
        notification.markRead(OffsetDateTime.now());
        notifications.saveAndFlush(notification);
    }

    public void markAllRead(Long actorId) {
        notifications.markAllRead(actorId, OffsetDateTime.now());
    }
}
