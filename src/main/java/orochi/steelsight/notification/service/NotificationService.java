package orochi.steelsight.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.notification.model.Notification;
import orochi.steelsight.notification.repository.NotificationRepository;

import java.time.OffsetDateTime;

/** Everyone can read their own notifications, and nobody else's. */
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;

    @Transactional(readOnly = true)
    public Page<Notification> listOwn(Long actorId, Pageable page) {
        return notifications.findAllByUserIdOrderByCreatedAtDescIdDesc(actorId, page);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long actorId) {
        return notifications.countByUserIdAndReadAtIsNull(actorId);
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
