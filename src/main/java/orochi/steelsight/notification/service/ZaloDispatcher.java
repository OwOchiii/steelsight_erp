package orochi.steelsight.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import orochi.steelsight.notification.model.NotificationKind;
import orochi.steelsight.notification.zalo.ZaloBot;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sends the Zalo copy of each saved notice (SRS 3.2.37). A failed message is tried again 1, 5 and 15
 * minutes later; when the third retry fails it is FAILED and the error is kept (BR-16). Texts over
 * 2,000 characters are cut at 1,900 and end with the link to the page (BR-17).
 *
 * Runs every minute from the job scheduler, and once more right after a notice is saved is not needed:
 * a minute is well inside what the SRS asks for alerts.
 */
@Service
@RequiredArgsConstructor
public class ZaloDispatcher {

    static final List<Duration> RETRIES = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
    static final int MAX_TEXT = 2000;
    static final int CUT_AT = 1900;
    private static final Locale SITE = Locale.forLanguageTag("vi");
    private static final Logger log = LoggerFactory.getLogger(ZaloDispatcher.class);

    private final JdbcTemplate jdbc;
    private final ZaloBot bot;
    private final NoticeText text;

    @Value("${steelsight.base-url:}")
    private String baseUrl;

    private record Due(long id, String chatId, NotificationKind kind, String params, String link, int attempts) {
    }

    /** Sends every delivery that is due; returns how many were sent. */
    public int sendDue() {
        if (!bot.canSend()) {
            return 0;
        }
        List<Due> due = jdbc.query("""
                SELECT d.notification_id, u.zalo_chat_id, n.kind, n.params::text, n.link, d.attempts
                FROM notification_delivery d
                         JOIN notification n ON n.id = d.notification_id
                         JOIN app_user u ON u.id = n.user_id
                WHERE d.status = 'PENDING' AND d.next_attempt_at <= now()
                ORDER BY d.next_attempt_at
                LIMIT 200
                FOR UPDATE OF d SKIP LOCKED""", (row, n) -> new Due(row.getLong(1), row.getString(2),
                NotificationKind.valueOf(row.getString(3)), row.getString(4), row.getString(5), row.getInt(6)));
        int sent = 0;
        for (Due delivery : due) {
            if (delivery.chatId() == null) {
                jdbc.update("UPDATE notification_delivery SET status = 'SKIPPED', next_attempt_at = NULL WHERE notification_id = ?",
                        delivery.id());
                continue;
            }
            Optional<String> error = bot.sendNow(delivery.chatId(), message(delivery));
            if (error.isEmpty()) {
                jdbc.update("""
                        UPDATE notification_delivery SET status = 'SENT', sent_at = now(), next_attempt_at = NULL,
                               attempts = attempts + 1, last_error = NULL WHERE notification_id = ?""", delivery.id());
                sent++;
            } else {
                failed(delivery, error.get());
            }
        }
        return sent;
    }

    private void failed(Due delivery, String error) {
        int attempts = delivery.attempts() + 1;
        if (attempts > RETRIES.size()) {
            jdbc.update("""
                    UPDATE notification_delivery SET status = 'FAILED', next_attempt_at = NULL, attempts = ?, last_error = ?
                    WHERE notification_id = ?""", attempts, error, delivery.id());
            log.warn("Zalo delivery of notice {} failed for good: {}", delivery.id(), error);
        } else {
            jdbc.update("""
                    UPDATE notification_delivery SET next_attempt_at = now() + ?::interval, attempts = ?, last_error = ?
                    WHERE notification_id = ?""", RETRIES.get(attempts - 1).toSeconds() + " seconds", attempts, error, delivery.id());
        }
    }

    String message(Due delivery) {
        String body = text.render(delivery.kind(), NoticeText.params(delivery.params()), SITE);
        String link = delivery.link() == null || baseUrl.isBlank() ? "" : baseUrl.replaceAll("/$", "") + delivery.link();
        return fit(body, link);
    }

    /** BR-17: at most 2,000 characters; a longer text is cut at 1,900 and ends with the link. */
    static String fit(String body, String link) {
        String full = link.isEmpty() ? body : body + "\n" + link;
        if (full.length() <= MAX_TEXT) {
            return full;
        }
        return body.substring(0, CUT_AT) + "…" + (link.isEmpty() ? "" : "\n" + link);
    }
}
