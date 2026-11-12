package orochi.steelsight.notification.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.notification.model.SafetyAlert;
import orochi.steelsight.repository.BaseRepository;

import java.time.OffsetDateTime;

public interface SafetyAlertRepository extends BaseRepository<SafetyAlert, Long> {

    boolean existsByNotifiedTrueAndCameraCodeAndReceivedAtAfter(String cameraCode, OffsetDateTime since);

    /** Alerts with no camera, or a code that isn't a registered camera, share one quiet period. */
    @Query("""
            SELECT count(a) > 0 FROM SafetyAlert a
            WHERE a.notified = true AND a.receivedAt > :since
              AND (a.cameraCode IS NULL OR a.cameraCode NOT IN (SELECT c.cameraCode FROM Camera c))""")
    boolean anyNotifiedFromUnregisteredCameraSince(@Param("since") OffsetDateTime since);
}
