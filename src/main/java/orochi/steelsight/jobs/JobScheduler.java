package orochi.steelsight.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import orochi.steelsight.attendance.service.CheckOutHours;
import orochi.steelsight.calendar.SiteCalendar;
import orochi.steelsight.notification.service.ZaloDispatcher;
import orochi.steelsight.web.SiteTime;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Runs the scheduled jobs of SRS 3.2.38 in Vietnam time. Every minute it works out, for each job, the
 * latest time it was due; a job_run row for that time means it already ran, so a job never runs twice
 * for the same time, and a run missed while the ERP slept (Render spins down) runs once on the next
 * check, which comes within 60 seconds of the job's time or of startup.
 */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);
    /** A run missed by longer than this is skipped: its notices would come too late to help. */
    private static final Duration CATCH_UP = Duration.ofDays(3);

    private record Job(Function<LocalDateTime, LocalDateTime> latestDue, Function<LocalDateTime, String> run) {
    }

    private final JdbcTemplate jdbc;
    private final SiteTime siteTime;
    private final ZaloDispatcher zalo;
    private final CheckOutHours checkOutHours;
    private final boolean enabled;
    private final Map<String, Job> jobs = new LinkedHashMap<>();

    public JobScheduler(JdbcTemplate jdbc, SiteTime siteTime, ZaloDispatcher zalo, CheckOutHours checkOutHours,
                        SiteJobs site, @Value("${steelsight.jobs.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.siteTime = siteTime;
        this.zalo = zalo;
        this.checkOutHours = checkOutHours;
        this.enabled = enabled;
        jobs.put("day-end", new Job(now -> daily(now, LocalTime.of(23, 0)), site::dayEnd));
        jobs.put("morning", new Job(now -> daily(now, LocalTime.of(7, 0)), site::morning));
        jobs.put("hourly", new Job(now -> now.withMinute(0).withSecond(0).withNano(0), site::hourly));
        jobs.put("old-tickets", new Job(now -> daily(now, LocalTime.of(8, 0)), site::oldTickets));
        jobs.put("weekly-schedule", new Job(JobScheduler::saturdayEvening, site::weeklySchedule));
        jobs.put("month-end", new Job(JobScheduler::lastWorkingDayMorning, site::monthEnd));
        jobs.put("cancel-last-month-overtime", new Job(JobScheduler::secondDayAfterMidnight, site::cancelLastMonthOvertime));
        jobs.put("month-lock", new Job(JobScheduler::secondWorkingDayEnd, site::lockMonth));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        tick();
    }

    /** Every minute: hours for new check-out notices, Zalo deliveries, then any job that is due. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            checkOutHours.fill();       // before sending, so the Zalo message has its hours worked
        } catch (RuntimeException e) {
            log.warn("Check-out hours failed: {}", e.getMessage());
        }
        try {
            zalo.sendDue();
        } catch (RuntimeException e) {
            log.warn("Zalo delivery failed: {}", e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now(siteTime.zoneId());
        for (String name : jobs.keySet()) {
            LocalDateTime due = jobs.get(name).latestDue().apply(now);
            if (due != null && !due.isBefore(now.minus(CATCH_UP))) {
                runOnce(name, due);
            }
        }
    }

    /** Runs a job for one scheduled time unless it already ran for it. Returns its result, or null. */
    public String runOnce(String name, LocalDateTime scheduledFor) {
        List<Integer> claimed = jdbc.queryForList("""
                INSERT INTO job_run (job, scheduled_for) VALUES (?, ?)
                ON CONFLICT (job, scheduled_for) DO NOTHING RETURNING 1""", Integer.class, name, siteTime.toInstant(scheduledFor));
        if (claimed.isEmpty()) {
            return null;
        }
        String result;
        try {
            result = jobs.get(name).run().apply(scheduledFor);
        } catch (RuntimeException e) {
            result = "FAILED: " + e.getMessage();
            log.error("Job {} for {} failed", name, scheduledFor, e);
        }
        jdbc.update("UPDATE job_run SET finished_at = now(), result = ? WHERE job = ? AND scheduled_for = ?",
                result, name, siteTime.toInstant(scheduledFor));
        log.info("Job {} for {}: {}", name, scheduledFor, result);
        return result;
    }

    static LocalDateTime daily(LocalDateTime now, LocalTime at) {
        LocalDateTime today = now.toLocalDate().atTime(at);
        return now.isBefore(today) ? today.minusDays(1) : today;
    }

    static LocalDateTime saturdayEvening(LocalDateTime now) {
        LocalDate day = now.toLocalDate();
        while (day.getDayOfWeek() != DayOfWeek.SATURDAY) {
            day = day.minusDays(1);
        }
        LocalDateTime at = day.atTime(18, 0);
        return now.isBefore(at) ? at.minusWeeks(1) : at;
    }

    static LocalDateTime lastWorkingDayMorning(LocalDateTime now) {
        YearMonth month = YearMonth.from(now);
        LocalDateTime at = SiteCalendar.lastWorkingDay(month).atTime(8, 0);
        return now.isBefore(at) ? SiteCalendar.lastWorkingDay(month.minusMonths(1)).atTime(8, 0) : at;
    }

    static LocalDateTime secondDayAfterMidnight(LocalDateTime now) {
        YearMonth month = YearMonth.from(now);
        LocalDateTime at = month.atDay(2).atTime(0, 5);
        return now.isBefore(at) ? month.minusMonths(1).atDay(2).atTime(0, 5) : at;
    }

    static LocalDateTime secondWorkingDayEnd(LocalDateTime now) {
        YearMonth month = YearMonth.from(now);
        LocalDateTime at = SiteCalendar.nthWorkingDay(month, 2).atTime(23, 59);
        return now.isBefore(at) ? SiteCalendar.nthWorkingDay(month.minusMonths(1), 2).atTime(23, 59) : at;
    }
}
