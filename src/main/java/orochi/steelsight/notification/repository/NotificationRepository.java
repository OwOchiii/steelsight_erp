package orochi.steelsight.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.notification.model.Notification;
import orochi.steelsight.repository.BaseRepository;

import java.time.OffsetDateTime;

public interface NotificationRepository extends BaseRepository<Notification, Long> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable page);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("at") OffsetDateTime at);
}
