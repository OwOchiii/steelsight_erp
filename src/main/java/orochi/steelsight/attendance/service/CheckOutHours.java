package orochi.steelsight.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Adds "hours worked" to each new CHECK_OUT notice (SRS 1.2.4: "Check-out at HH:MM, hours worked N.NN").
 * The notice is made by a database trigger when the detection app writes the check-out (V23); the paid
 * hours of the shift so far follow the late, early and buffer rules of {@link ShiftAttendance}, so they
 * are worked out here, just before the Zalo message is sent. A visit with no shift gets no hours ("-").
 */
@Service
@RequiredArgsConstructor
public class CheckOutHours {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CheckOutHours.class);

    private final JdbcTemplate jdbc;
    private final AttendanceCalculator calculator;

    private record Pending(long notificationId, long userId, Long shiftId) {
    }

    /**
     * Gives every CHECK_OUT notice still without an "hours" key its hours, and returns how many. ZaloDispatcher
     * holds back the Zalo copy of a CHECK_OUT notice until the key exists, so a notice saved between this and
     * the send waits for the next minute instead of going out without hours.
     *
     * Every notice gets the key, so none waits for ever: the paid hours, or null ("-") for a visit with no
     * shift, and also null, with a warning in the log, when the hours can't be worked out (the shift is gone,
     * or the calculation fails). One failing notice doesn't hold up the others; for that there is no
     * transaction around the loop, since a failed query would abort it for every notice after.
     */
    public int fill() {
        List<Pending> pending = jdbc.query("""
                SELECT n.id, n.user_id, (n.params ->> 'shiftId')::bigint FROM notification n
                WHERE n.kind = 'CHECK_OUT' AND NOT (n.params ? 'hours')
                  AND (n.created_at > now() - interval '2 days'
                       OR EXISTS (SELECT 1 FROM notification_delivery d WHERE d.notification_id = n.id AND d.status = 'PENDING'))
                ORDER BY n.id""",
                (row, i) -> new Pending(row.getLong(1), row.getLong(2), (Long) row.getObject(3, Long.class)));
        for (Pending notice : pending) {
            String hours = null;
            if (notice.shiftId() != null) {
                try {
                    hours = paidHours(notice.userId(), notice.shiftId())
                            .map(value -> value.setScale(2, RoundingMode.HALF_UP).toPlainString()).orElse(null);
                    if (hours == null) {
                        log.warn("Check-out notice {}: shift {} not found, sent without hours", notice.notificationId(), notice.shiftId());
                    }
                } catch (RuntimeException e) {
                    log.warn("Check-out notice {}: hours could not be worked out, sent without hours: {}",
                            notice.notificationId(), e.getMessage());
                }
            }
            jdbc.update("UPDATE notification SET params = params || jsonb_build_object('hours', ?::text) WHERE id = ?",
                    hours, notice.notificationId());
        }
        return pending.size();
    }

    private Optional<BigDecimal> paidHours(long userId, long shiftId) {
        List<OffsetDateTime> start = jdbc.queryForList("SELECT start_at FROM shift WHERE id = ?", OffsetDateTime.class, shiftId);
        if (start.isEmpty()) {
            return Optional.empty();
        }
        return calculator.rows(List.of(userId), start.get(0), start.get(0).plusSeconds(1)).stream()
                .filter(row -> row.result().shift().id() == shiftId)
                .map(row -> row.result().paidHours())
                .findFirst();
    }
}
