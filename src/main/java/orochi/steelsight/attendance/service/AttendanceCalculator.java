package orochi.steelsight.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.calendar.SiteCalendar;
import orochi.steelsight.ticketing.model.LeaveType;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads shifts, visits, approved leave and pending overtime, and works out each shift with
 * ShiftAttendance (SRS 3.2.39). The one place every page and the monthly report get their numbers.
 * No permission check: callers decide whose attendance a user may see.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AttendanceCalculator {

    private final JdbcTemplate jdbc;
    private final SiteCalendar calendar;

    /** One shift of one person, with its zone and its result. */
    public record Row(Long userId, String zone, String kind, String status, ShiftAttendance.Result result) {
    }

    /** Every shift that starts in [from, to) of these people, not cancelled, in start order. */
    public List<Row> rows(Collection<Long> userIds, OffsetDateTime from, OffsetDateTime to) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Long[] ids = userIds.toArray(Long[]::new);
        record ShiftLine(long id, long userId, OffsetDateTime start, OffsetDateTime end, String kind, String status, String zone) {
        }
        List<ShiftLine> shifts = jdbc.query("""
                SELECT s.id, s.user_id, s.start_at, s.end_at, s.kind, s.status, z.name
                FROM shift s JOIN zone z ON z.id = s.zone_id
                WHERE s.user_id = ANY (?) AND s.status <> 'CANCELLED' AND s.start_at >= ? AND s.start_at < ?
                ORDER BY s.start_at, s.id""", (row, n) -> new ShiftLine(row.getLong(1), row.getLong(2), time(row.getTimestamp(3)),
                time(row.getTimestamp(4)), row.getString(5), row.getString(6), row.getString(7)), ids, from, to);
        if (shifts.isEmpty()) {
            return List.of();
        }
        Long[] shiftIds = shifts.stream().map(ShiftLine::id).toArray(Long[]::new);

        Map<Long, List<ShiftAttendance.Visit>> visits = new HashMap<>();
        jdbc.query("""
                SELECT s.id AS shift_id, c.id, c.check_in_at, c.check_out_at, c.auto_closed, c.incomplete, c.incomplete_at,
                       (c.method = 'MANUAL' OR EXISTS (SELECT 1 FROM check_in_correction k WHERE k.check_in_id = c.id)) AS edited
                FROM shift s JOIN check_in c ON c.user_id = s.user_id
                WHERE s.id = ANY (?)
                  AND (c.shift_id = s.id
                       OR (c.shift_id IS NULL AND c.check_in_at < s.end_at AND COALESCE(c.check_out_at, now()) > s.start_at))
                ORDER BY c.check_in_at""", row -> {
            visits.computeIfAbsent(row.getLong(1), k -> new ArrayList<>()).add(new ShiftAttendance.Visit(row.getLong(2),
                    time(row.getTimestamp(3)), time(row.getTimestamp(4)), row.getBoolean(5), row.getBoolean(6),
                    time(row.getTimestamp(7)), row.getBoolean(8)));
        }, (Object) shiftIds);

        Map<Long, List<ShiftAttendance.Leave>> leaves = new HashMap<>();
        jdbc.query("""
                SELECT t.shift_id, t.leave_type, t.leave_from, t.leave_to
                FROM ticket t JOIN ticket_category c ON c.id = t.category_id
                WHERE t.shift_id = ANY (?) AND c.code = 'LEAVE' AND t.status = 'RESOLVED' AND t.outcome = 'APPROVED'
                  AND t.deleted_at IS NULL""", row -> {
            leaves.computeIfAbsent(row.getLong(1), k -> new ArrayList<>()).add(new ShiftAttendance.Leave(
                    LeaveType.valueOf(row.getString(2)), time(row.getTimestamp(3)), time(row.getTimestamp(4))));
        }, (Object) shiftIds);

        Map<Long, List<ShiftAttendance.PendingOvertime>> pending = new HashMap<>();
        jdbc.query("""
                SELECT shift_id, status, start_at, end_at, expires_at FROM pending_overtime WHERE shift_id = ANY (?)""", row -> {
            pending.computeIfAbsent(row.getLong(1), k -> new ArrayList<>()).add(new ShiftAttendance.PendingOvertime(
                    row.getString(2), time(row.getTimestamp(3)), time(row.getTimestamp(4)), time(row.getTimestamp(5))));
        }, (Object) shiftIds);

        Map<String, Object> setting = jdbc.queryForMap(
                "SELECT half_day_unexplained, explanation_working_days FROM company_setting");
        boolean halfDay = (Boolean) setting.get("half_day_unexplained");
        int explanationDays = ((Number) setting.get("explanation_working_days")).intValue();
        OffsetDateTime now = OffsetDateTime.now();

        List<Row> rows = new ArrayList<>();
        for (ShiftLine line : shifts) {
            ShiftAttendance.Result result = ShiftAttendance.of(
                    new ShiftAttendance.Shift(line.id(), line.start(), line.end(), "OVERTIME".equals(line.kind())),
                    visits.getOrDefault(line.id(), List.of()), leaves.getOrDefault(line.id(), List.of()),
                    pending.getOrDefault(line.id(), List.of()), now,
                    flagged -> calendar.deadline(flagged, explanationDays), halfDay);
            rows.add(new Row(line.userId(), line.zone(), line.kind(), line.status(), result));
        }
        rows.sort(Comparator.comparing((Row r) -> r.result().shift().start()));
        return rows;
    }

    public ShiftAttendance.Summary summary(List<Row> rows) {
        return ShiftAttendance.Summary.of(rows.stream().map(Row::result).toList());
    }

    private static OffsetDateTime time(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
}
