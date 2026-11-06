package orochi.steelsight.notification.repository;

import orochi.steelsight.notification.model.SafetyAlert;
import orochi.steelsight.repository.BaseRepository;

import java.time.OffsetDateTime;

public interface SafetyAlertRepository extends BaseRepository<SafetyAlert, Long> {

    boolean existsByNotifiedTrueAndReceivedAtAfter(OffsetDateTime since);
}
