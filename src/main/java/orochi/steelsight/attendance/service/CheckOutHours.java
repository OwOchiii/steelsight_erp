package orochi.steelsight.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final JdbcTemplate jdbc;
    private final AttendanceCalculator calculator;

    private record Pending(long notificationId, long userId, Long shiftId) {
    }

    /** Returns how many notices got their hours. */
    @Transactional
    public int fill() {
        List<Pending> pending = jdbc.query("""
                SELECT id, user_id, (params ->> 'shiftId')::bigint FROM notification
                WHERE kind = 'CHECK_OUT' AND NOT (params ? 'hours') AND created_at > now() - interval '2 days'
                ORDER BY id""",
                (row, i) -> new Pending(row.getLong(1), row.getLong(2), (Long) row.getObject(3, Long.class)));
        for (Pending notice : pending) {
            String hours = notice.shiftId() == null ? null : paidHours(notice.userId(), notice.shiftId())
                    .map(value -> value.setScale(2, RoundingMode.HALF_UP).toPlainString()).orElse(null);
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
