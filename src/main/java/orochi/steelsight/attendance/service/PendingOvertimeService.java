package orochi.steelsight.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.ShiftKind;
import orochi.steelsight.web.ShiftForm;
import orochi.steelsight.web.SiteTime;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Pending overtime (SRS 3.2.15, BR-34, BR-37): time worked after the shift end plus 20 minutes without an
 * overtime shift. Paid at 0% until the Admin adds an OVERTIME shift that covers it within 48 hours; then
 * cancelled, and it can't be made valid again. The 23:00 job creates it; the hourly job cancels it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PendingOvertimeService {

    static final Duration VALID_FOR = Duration.ofHours(48);

    private final AccessPolicy access;
    private final JdbcTemplate jdbc;
    private final SiteTime siteTime;

    public record Row(long id, long userId, String name, String code, String team, OffsetDateTime start,
                      OffsetDateTime end, BigDecimal hours, OffsetDateTime expiresAt, String leaderNote) {

        public boolean endsSoon() {
            return Duration.between(OffsetDateTime.now(), expiresAt).compareTo(Duration.ofHours(12)) < 0;
        }
    }

    /** The Admin's list, or a team leader's own team. teamId null shows every team. */
    @Transactional(readOnly = true)
    public List<Row> listOpen(Long actorId, Long teamId) {
        boolean admin = access.has(actorId, Permissions.SHIFT_UPDATE_ALL);
        if (!admin) {
            access.require(actorId, Permissions.SHIFT_READ_TEAM);
        }
        return jdbc.query("""
                SELECT p.id, p.user_id, u.full_name, u.employee_code, t.name, p.start_at, p.end_at, p.expires_at, p.leader_note
                FROM pending_overtime p JOIN app_user u ON u.id = p.user_id LEFT JOIN team t ON t.id = u.team_id
                WHERE p.status = 'PENDING' AND p.expires_at > now()
                  AND (? OR t.manager_id = ?)
                  AND (?::bigint IS NULL OR u.team_id = ?)
                ORDER BY p.expires_at""", (row, n) -> {
            OffsetDateTime start = time(row.getTimestamp(6));
            OffsetDateTime end = time(row.getTimestamp(7));
            return new Row(row.getLong(1), row.getLong(2), row.getString(3), row.getString(4), row.getString(5), start, end,
                    ShiftAttendance.hoursOf(Duration.between(start, end)), time(row.getTimestamp(8)), row.getString(9));
        }, admin, actorId, teamId, teamId);
    }

    /** Fills the Shift Form with an OVERTIME shift over this pending time (SRS 3.2.15 #9). */
    @Transactional(readOnly = true)
    public void prefill(Long actorId, Long pendingId, ShiftForm form) {
        access.require(actorId, Permissions.SHIFT_UPDATE_ALL);
        var row = jdbc.queryForMap("""
                SELECT p.user_id, p.start_at, p.end_at, s.zone_id, p.status, p.expires_at
                FROM pending_overtime p JOIN shift s ON s.id = p.shift_id WHERE p.id = ?""", pendingId);
        OffsetDateTime start = time((Timestamp) row.get("start_at"));
        OffsetDateTime end = time((Timestamp) row.get("end_at"));
        form.setWorkerId(((Number) row.get("user_id")).longValue());
        form.setZoneId(((Number) row.get("zone_id")).longValue());
        form.setKind(ShiftKind.OVERTIME);
        form.setDay(siteTime.dateOf(start));
        form.setStart(siteTime.toLocal(start).toLocalTime().withSecond(0).withNano(0));
        form.setEnd(siteTime.toLocal(end).toLocalTime().withSecond(0).withNano(0).plusMinutes(1));
        form.setPendingOvertimeId(pendingId);
    }

    /** "Cancel selected": the Admin cancels pending overtime at once. */
    public int cancel(Long actorId, List<Long> ids) {
        access.require(actorId, Permissions.SHIFT_UPDATE_ALL);
        if (ids == null || ids.isEmpty()) {
            throw new BusinessRuleException("Choose the pending overtime to cancel");
        }
        return jdbc.update("UPDATE pending_overtime SET status = 'CANCELLED', decided_at = now() WHERE status = 'PENDING' AND id = ANY (?)",
                (Object) ids.toArray(Long[]::new));
    }

    /** The team leader says who really worked overtime (SRS 3.2.15: by a comment). */
    public void leaderNote(Long actorId, Long pendingId, String note) {
        access.require(actorId, Permissions.SHIFT_READ_TEAM);
        String text = note == null ? null : note.strip();
        if (text != null && text.length() > 500) {
            throw new BusinessRuleException("A note can be at most 500 characters");
        }
        int changed = jdbc.update("""
                UPDATE pending_overtime p SET leader_note = ?
                FROM app_user u JOIN team t ON t.id = u.team_id
                WHERE p.id = ? AND u.id = p.user_id AND t.manager_id = ? AND p.status = 'PENDING'""", text, pendingId, actorId);
        if (changed == 0) {
            throw new BusinessRuleException("This pending overtime is not of your team");
        }
    }

    /**
     * 23:00 job (BR-34): every visit that ended more than 20 minutes after its REGULAR shift, with no
     * OVERTIME shift of that person covering the time, gets its pending overtime. Idempotent.
     */
    public int createFromVisits() {
        return jdbc.update("""
                INSERT INTO pending_overtime (check_in_id, user_id, shift_id, start_at, end_at, expires_at)
                SELECT c.id, c.user_id, s.id, s.end_at + interval '20 minutes', c.check_out_at,
                       LEAST(now() + interval '48 hours',
                             (date_trunc('month', c.check_out_at AT TIME ZONE p.time_zone) + interval '1 month 1 day')
                                 AT TIME ZONE p.time_zone)
                FROM check_in c JOIN shift s ON s.id = c.shift_id CROSS JOIN safety_policy p
                WHERE s.kind = 'REGULAR' AND s.status <> 'CANCELLED'
                  AND c.check_out_at IS NOT NULL AND NOT c.auto_closed AND NOT c.incomplete
                  AND c.check_out_at > s.end_at + interval '20 minutes'
                  AND NOT EXISTS (SELECT 1 FROM shift o WHERE o.user_id = c.user_id AND o.kind = 'OVERTIME'
                                  AND o.status <> 'CANCELLED' AND o.start_at < c.check_out_at
                                  AND o.end_at > s.end_at + interval '20 minutes')
                ON CONFLICT (check_in_id) DO NOTHING""");
    }

    /** Hourly job and 2nd of the month (BR-37): cancels what is past its time. */
    public int expire() {
        return jdbc.update("UPDATE pending_overtime SET status = 'CANCELLED', decided_at = now() WHERE status = 'PENDING' AND expires_at <= now()");
    }

    private static OffsetDateTime time(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
}
