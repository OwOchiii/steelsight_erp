package orochi.steelsight.jobs;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.service.PendingOvertimeService;
import orochi.steelsight.notification.model.NotificationKind;
import orochi.steelsight.notification.service.NotificationService;
import orochi.steelsight.notification.service.Notices;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.repository.ShiftRepository;
import orochi.steelsight.web.SiteTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What each scheduled job does (SRS 3.2.38). JobScheduler decides when; every job is idempotent, so a
 * repeat run changes nothing and sends nothing twice (RE-08).
 */
@Service
@RequiredArgsConstructor
public class SiteJobs {

    private static final Logger log = LoggerFactory.getLogger(SiteJobs.class);

    private final JdbcTemplate jdbc;
    private final PendingOvertimeService overtime;
    private final NotificationService notifications;
    private final Notices notices;
    private final ShiftRepository shifts;
    private final SiteTime siteTime;

    /**
     * Shifts that have ended become COMPLETED by themselves; nobody has to mark them. A check-out already
     * completes its shift (V25), so this catches the shifts nobody checked out of. Locked months are
     * left as they are (BR-70). The version goes up, so a form opened before stays stale.
     */
    int completeEndedShifts() {
        return jdbc.update("""
                UPDATE shift s SET status = 'COMPLETED', version = s.version + 1, updated_at = now()
                WHERE s.status = 'SCHEDULED' AND s.end_at <= now()
                  AND NOT EXISTS (SELECT 1 FROM month_lock m
                                  WHERE m.month = date_trunc('month', s.start_at AT TIME ZONE ?)::date)""",
                siteTime.zoneId().getId());
    }

    /**
     * 23:00: flags Incomplete attendance and closes visits open 4 hours after the shift (BR-35); splits
     * time after the shift end plus 20 minutes into pending overtime (BR-34); sets the day's
     * PENDING_REVIEW violations to OVERDUE (BR-53).
     */
    @Transactional
    public String dayEnd(LocalDateTime scheduledFor) {
        List<Long> before = flaggedIds();
        jdbc.queryForObject("SELECT close_stale_check_ins(?)", Integer.class, siteTime.zoneId().getId());
        List<Long> flagged = flaggedIds();
        flagged.removeAll(before);
        jdbc.query("SELECT id, user_id, check_in_at FROM check_in WHERE id = ANY (?)", row -> {
            notifications.notify(row.getLong(2), NotificationKind.INCOMPLETE_ATTENDANCE,
                    Map.of("checkInAt", row.getObject(3, OffsetDateTime.class).toString()), "/attendance/mine");
        }, (Object) flagged.toArray(Long[]::new));
        int pending = overtime.createFromVisits();
        int completed = completeEndedShifts();
        int overdue = jdbc.update("UPDATE violation SET review_status = 'OVERDUE' WHERE review_status = 'PENDING_REVIEW' AND detected_at < ?",
                siteTime.toInstant(scheduledFor));
        return "incomplete=" + flagged.size() + " pendingOvertime=" + pending + " overdue=" + overdue
                + " completed=" + completed;
    }

    private List<Long> flaggedIds() {
        return new java.util.ArrayList<>(jdbc.queryForList("SELECT id FROM check_in WHERE incomplete", Long.class));
    }

    /** 07:00: the Admin gets the overdue violations and the pending overtime; each team leader his team's. */
    @Transactional
    public String morning(LocalDateTime scheduledFor) {
        long overdue = count("SELECT count(*) FROM violation WHERE review_status = 'OVERDUE'");
        long pending = count("SELECT count(*) FROM pending_overtime WHERE status = 'PENDING' AND expires_at > now()");
        List<Long> admins = usersInRole("ADMIN");
        if (overdue > 0) {
            notifications.notifyAll(admins, NotificationKind.OVERDUE_VIOLATIONS, Map.of("count", overdue), "/violations/review");
        }
        if (pending > 0) {
            notifications.notifyAll(admins, NotificationKind.PENDING_OVERTIME_LIST, Map.of("count", pending), "/overtime");
        }
        jdbc.query("""
                SELECT t.manager_id, count(*) FROM pending_overtime p JOIN app_user u ON u.id = p.user_id
                JOIN team t ON t.id = u.team_id
                WHERE p.status = 'PENDING' AND p.expires_at > now() AND t.manager_id IS NOT NULL
                GROUP BY t.manager_id""", row -> {
            notifications.notify(row.getLong(1), NotificationKind.PENDING_OVERTIME_LIST, Map.of("count", row.getLong(2)), "/attendance/team");
        });
        return "overdue=" + overdue + " pendingOvertime=" + pending;
    }

    /**
     * Every hour: cancels pending overtime past its 48 hours, closes visits open 4 hours after the shift,
     * and marks ended shifts COMPLETED.
     */
    @Transactional
    public String hourly(LocalDateTime scheduledFor) {
        int expired = overtime.expire();
        jdbc.queryForObject("SELECT close_stale_check_ins(?)", Integer.class, siteTime.zoneId().getId());
        int completed = completeEndedShifts();
        return "expired=" + expired + " completed=" + completed;
    }

    /** 08:00: the Admin is told of tickets waiting more than 3 working days (BR-69). */
    @Transactional
    public String oldTickets(LocalDateTime scheduledFor) {
        LocalDate today = scheduledFor.toLocalDate();
        long old = jdbc.queryForList("SELECT created_at FROM ticket WHERE status IN ('OPEN', 'IN_PROGRESS') AND deleted_at IS NULL",
                        OffsetDateTime.class).stream()
                .filter(created -> workingDaysBetween(siteTime.dateOf(created), today) > 3).count();
        if (old > 0) {
            notifications.notifyAll(usersInRole("ADMIN"), NotificationKind.OLD_TICKETS, Map.of("count", old), "/tickets/queue");
        }
        return "old=" + old;
    }

    /** Saturday 18:00: each active employee gets the shifts of the next week, or "no shift" (BR-18). */
    @Transactional
    public String weeklySchedule(LocalDateTime scheduledFor) {
        LocalDate monday = siteTime.mondayOf(scheduledFor.toLocalDate()).plusWeeks(1);
        Map<Long, List<Shift>> byPerson = shifts.findAllByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        siteTime.startOf(monday), siteTime.startOf(monday.plusWeeks(1))).stream()
                .filter(shift -> shift.getStatus() != ShiftStatus.CANCELLED)
                .collect(Collectors.groupingBy(shift -> shift.getUser().getId()));
        List<Long> people = jdbc.queryForList("SELECT id FROM app_user WHERE is_active AND deleted_at IS NULL", Long.class);
        for (Long person : people) {
            notices.weekSchedule(person, monday, byPerson.getOrDefault(person, List.of()));
        }
        return "people=" + people.size();
    }

    /** Last working day of the month, 08:00: open tickets for the Admin, open violations for HSE (BR-69). */
    @Transactional
    public String monthEnd(LocalDateTime scheduledFor) {
        long tickets = count("SELECT count(*) FROM ticket WHERE status IN ('OPEN', 'IN_PROGRESS') AND deleted_at IS NULL");
        long violations = count("SELECT count(*) FROM violation WHERE review_status IN ('PENDING_REVIEW', 'OVERDUE')");
        if (tickets > 0) {
            notifications.notifyAll(usersInRole("ADMIN"), NotificationKind.OPEN_TICKETS, Map.of("count", tickets), "/tickets/queue");
        }
        if (violations > 0) {
            notifications.notifyAll(usersInRole("HSE_OFFICER"), NotificationKind.OVERDUE_VIOLATIONS, Map.of("count", violations), "/violations/review");
        }
        return "tickets=" + tickets + " violations=" + violations;
    }

    /** 2nd of the month, 00:05: what is left of last month's pending overtime is cancelled (BR-37). */
    @Transactional
    public String cancelLastMonthOvertime(LocalDateTime scheduledFor) {
        int cancelled = jdbc.update("""
                UPDATE pending_overtime SET status = 'CANCELLED', decided_at = now()
                WHERE status = 'PENDING' AND start_at < ?""", siteTime.startOf(scheduledFor.toLocalDate().withDayOfMonth(1)));
        return "cancelled=" + cancelled;
    }

    /**
     * 23:59 of the 2nd working day: locks last month and freezes every Site employee's score and bonus
     * level (BR-70). The scores are read before the lock row exists, so they come from the live rules.
     */
    @Transactional
    public String lockMonth(LocalDateTime scheduledFor) {
        YearMonth month = YearMonth.from(scheduledFor).minusMonths(1);
        return lock(month);
    }

    @Transactional
    public String lock(YearMonth month) {
        OffsetDateTime inMonth = siteTime.startOf(month.atDay(1));
        int frozen = jdbc.update("""
                INSERT INTO monthly_score (user_id, month, violations, score, level)
                SELECT user_id, ?, violations, score, level FROM safety_scores(?)
                ON CONFLICT (user_id, month) DO NOTHING""", month.atDay(1), inMonth);
        jdbc.update("INSERT INTO month_lock (month) VALUES (?) ON CONFLICT (month) DO NOTHING", month.atDay(1));
        log.info("Month {} locked, {} scores frozen", month, frozen);
        return "frozen=" + frozen;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private List<Long> usersInRole(String role) {
        return jdbc.queryForList("""
                SELECT u.id FROM app_user u JOIN role r ON r.id = u.role_id
                WHERE r.role_name = ? AND u.is_active AND u.deleted_at IS NULL""", Long.class, role);
    }

    private static int workingDaysBetween(LocalDate from, LocalDate to) {
        int days = 0;
        for (LocalDate day = from.plusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            if (orochi.steelsight.calendar.SiteCalendar.isWorkingDay(day)) {
                days++;
            }
        }
        return days;
    }
}
