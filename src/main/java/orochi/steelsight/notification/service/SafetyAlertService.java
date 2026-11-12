package orochi.steelsight.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.notification.model.Notification;
import orochi.steelsight.notification.model.SafetyAlert;
import orochi.steelsight.notification.repository.NotificationRepository;
import orochi.steelsight.notification.repository.SafetyAlertRepository;
import orochi.steelsight.ppe.repository.CameraRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Takes alerts from the detection software, which decides for itself when violations are too many. */
@Service
@Transactional
@RequiredArgsConstructor
public class SafetyAlertService {

    private static final Logger log = LoggerFactory.getLogger(SafetyAlertService.class);
    private static final Duration QUIET_AFTER_NOTIFYING = Duration.ofMinutes(15);
    private static final Duration OLDEST_TO_NOTIFY = Duration.ofHours(1);
    private static final Duration FURTHEST_AHEAD_TO_NOTIFY = Duration.ofMinutes(5);
    private static final String HSE_ROLE = "HSE_OFFICER";

    private final JdbcTemplate jdbc;
    private final SafetyAlertRepository alerts;
    private final CameraRepository cameras;
    private final NotificationRepository notifications;
    private final AppUserRepository users;
    private final RoleRepository roles;

    public record NewAlert(UUID eventId, int violationCount, int windowSeconds, OffsetDateTime detectedAt, String cameraCode) {
    }

    /**
     * Every new alert is kept, but HSE only hears again once the quiet time since that source's last
     * notice has passed. Detection retries until a call is confirmed, so a known event id changes nothing.
     *
     * @return false if this event id had already arrived
     */
    public boolean record(NewAlert alert) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean notify = isCurrent(alert, now) && !sourceWasJustNotified(alert.cameraCode(), now);
        List<Long> created = jdbc.queryForList("""
                INSERT INTO safety_alert (event_id, violation_count, window_seconds, detected_at, camera_code, notified)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                RETURNING id""", Long.class, alert.eventId(), alert.violationCount(), alert.windowSeconds(),
                alert.detectedAt(), alert.cameraCode(), notify);
        if (created.isEmpty()) {
            return false;
        }
        if (notify) {
            tellHse(alerts.getReferenceById(created.get(0)), now);
        }
        return true;
    }

    /** An alert from too long ago, or from the future, is kept but tells nobody and starts no quiet time. */
    private boolean isCurrent(NewAlert alert, OffsetDateTime now) {
        boolean current = !alert.detectedAt().isBefore(now.minus(OLDEST_TO_NOTIFY))
                && !alert.detectedAt().isAfter(now.plus(FURTHEST_AHEAD_TO_NOTIFY));
        if (!current) {
            log.warn("Safety alert {} not notified: detected at {}, now {}", alert.eventId(), alert.detectedAt(), now);
        }
        return current;
    }

    /**
     * Each registered camera has its own quiet time, so an alert from one camera can't silence the others.
     * Any other code shares a single quiet time, so that inventing camera codes can't flood HSE.
     */
    private boolean sourceWasJustNotified(String cameraCode, OffsetDateTime now) {
        OffsetDateTime since = now.minus(QUIET_AFTER_NOTIFYING);
        boolean registered = cameraCode != null && cameras.findByCameraCode(cameraCode).isPresent();
        return registered
                ? alerts.existsByNotifiedTrueAndCameraCodeAndReceivedAtAfter(cameraCode, since)
                : alerts.anyNotifiedFromUnregisteredCameraSince(since);
    }

    private void tellHse(SafetyAlert alert, OffsetDateTime now) {
        List<AppUser> officers = roles.findByRoleName(HSE_ROLE)
                .map(role -> users.findActiveByRole(role.getId()))
                .orElse(List.of());
        if (officers.isEmpty()) {
            log.warn("Safety alert {} has no active HSE officer to notify", alert.getId());
        }
        officers.forEach(officer -> notifications.save(Notification.hseAlert(officer, alert, now)));
    }
}
