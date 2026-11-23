package orochi.steelsight.reporting.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.service.AttendanceCalculator;
import orochi.steelsight.attendance.service.ShiftAttendance;
import orochi.steelsight.calendar.MonthLocks;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.StaffGroup;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.SafetyLevel;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.web.SiteTime;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The monthly Excel file for accounting (SRS 3.2.24, BR-71): Site summary, Office summary, Violations,
 * Attendance and Absences. Attendance comes from AttendanceCalculator, the same as My Attendance, and the
 * score from safety_scores(), which reads the frozen row once the month is locked. A file made before the
 * lock is Provisional. Every export is logged. No money is worked out (OR-02).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MonthlyReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);

    private final AccessPolicy access;
    private final AppUserRepository users;
    private final TeamRepository teams;
    private final AttendanceCalculator calculator;
    private final MonthLocks locks;
    private final SiteTime siteTime;
    private final JdbcTemplate jdbc;

    public record Period(YearMonth month, OffsetDateTime from, OffsetDateTime to) {
    }

    /** One employee's month. score and level are null for office staff, who have none (BR-56). */
    public record EmployeeRow(Long id, String code, String name, String team, ShiftAttendance.Summary attendance,
                              int violations, int reminders, Integer score, SafetyLevel level) {

        public Integer reductionPercent() {
            return level == null ? null : level.reductionPercent();
        }
    }

    /** person is null for a stranger. */
    public record ViolationRow(OffsetDateTime detectedAt, String zone, String type, String status, String code,
                               String person, String reviewer, String appeal) {
    }

    public record VisitRow(OffsetDateTime checkInAt, OffsetDateTime checkOutAt, String code, String name,
                           OffsetDateTime shiftStart, OffsetDateTime shiftEnd, String method, boolean incomplete,
                           boolean closedBySystem) {
    }

    public record AbsenceRow(OffsetDateTime shiftStart, OffsetDateTime shiftEnd, String code, String name, String zone) {
    }

    public record Export(OffsetDateTime exportedAt, YearMonth month, String scope, String by, boolean provisional) {
    }

    /** team is null for the whole site. */
    public record Report(Period period, String team, boolean provisional, OffsetDateTime generatedAt,
                         List<EmployeeRow> site, List<EmployeeRow> office, List<ViolationRow> violations,
                         List<VisitRow> visits, List<AbsenceRow> absences) {
    }

    public Report build(Long actorId, YearMonth month) {
        return build(actorId, month, null);
    }

    /** An Admin exports the whole site or one team; a Manager his own team (SRS 3.2.24 #2). */
    public Report build(Long actorId, YearMonth month, Long teamId) {
        Team team = scope(actorId, teamId);
        Period period = period(month);
        List<AppUser> people = (team == null
                ? users.findAllByDeletedAtIsNull()
                : users.findAllByTeamIdAndDeletedAtIsNull(team.getId())).stream()
                .sorted(Comparator.comparing(AppUser::getFullName).thenComparing(AppUser::getId))
                .toList();
        Map<Long, AppUser> byId = people.stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<AttendanceCalculator.Row> rows = calculator.rows(byId.keySet(), period.from(), period.to());
        Map<Long, List<AttendanceCalculator.Row>> perPerson = rows.stream().collect(Collectors.groupingBy(AttendanceCalculator.Row::userId));

        Map<Long, Integer> scores = new HashMap<>();
        Map<Long, SafetyLevel> levels = new HashMap<>();
        Map<Long, Integer> counted = new HashMap<>();
        jdbc.query("SELECT user_id, score, level, violations FROM safety_scores(?)", row -> {
            scores.put(row.getLong(1), row.getInt(2));
            levels.put(row.getLong(1), SafetyLevel.of(row.getInt(3)));
            counted.put(row.getLong(1), row.getInt(4));
        }, period.from());
        Map<Long, Integer> reminders = new HashMap<>();
        jdbc.query("""
                SELECT user_id, count(*) FROM violation WHERE review_status = 'REMINDER'
                  AND detected_at >= ? AND detected_at < ? GROUP BY user_id""",
                row -> {
                    reminders.put(row.getLong(1), row.getInt(2));
                }, period.from(), period.to());

        List<EmployeeRow> site = new ArrayList<>();
        List<EmployeeRow> office = new ArrayList<>();
        for (AppUser person : people) {
            ShiftAttendance.Summary attendance = calculator.summary(perPerson.getOrDefault(person.getId(), List.of()));
            EmployeeRow row = new EmployeeRow(person.getId(), person.getEmployeeCode(), person.getFullName(),
                    person.getTeam() == null ? null : person.getTeam().getName(), attendance,
                    counted.getOrDefault(person.getId(), 0), reminders.getOrDefault(person.getId(), 0),
                    scores.get(person.getId()), levels.get(person.getId()));
            (person.getStaffGroup() == StaffGroup.SITE && scores.containsKey(person.getId()) ? site : office).add(row);
        }

        List<AbsenceRow> absences = rows.stream()
                .filter(r -> r.result().has(ShiftAttendance.Flag.ABSENT))
                .map(r -> new AbsenceRow(r.result().shift().start(), r.result().shift().end(),
                        byId.get(r.userId()).getEmployeeCode(), byId.get(r.userId()).getFullName(), r.zone()))
                .toList();

        Report report = new Report(period, team == null ? null : team.getName(), !locks.isLocked(month), OffsetDateTime.now(),
                site, office, violations(period, team, byId.keySet()), visits(period, byId.keySet()), absences);
        log.info("Monthly report for {} built by user {} ({} site, {} office)", month, actorId, site.size(), office.size());
        return report;
    }

    /** Logs one export: who, which month and scope, and whether it was provisional (BR-71). */
    public void logExport(Long actorId, Report report) {
        jdbc.update("INSERT INTO report_export (user_id, month, scope, provisional) VALUES (?, ?, ?, ?)",
                actorId, report.period().month().atDay(1), report.team() == null ? "SITE" : report.team(), report.provisional());
    }

    /** The earlier exports the actor may see: all for the Admin, his own for a Manager. */
    @Transactional(readOnly = true)
    public List<Export> exports(Long actorId) {
        boolean all = access.has(actorId, Permissions.REPORT_EXPORT_ALL);
        if (!all) {
            access.require(actorId, Permissions.REPORT_EXPORT_TEAM);
        }
        return jdbc.query("""
                SELECT e.exported_at, e.month, e.scope, u.full_name, e.provisional
                FROM report_export e JOIN app_user u ON u.id = e.user_id
                WHERE ? OR e.user_id = ?
                ORDER BY e.exported_at DESC LIMIT 50""", (row, n) -> new Export(time(row.getTimestamp(1)),
                YearMonth.from(row.getDate(2).toLocalDate()), row.getString(3), row.getString(4), row.getBoolean(5)), all, actorId);
    }

    /** Every confirmed violation, reminder and stranger of the month (BR-71). A team's file has the team's. */
    private List<ViolationRow> violations(Period period, Team team, Set<Long> people) {
        List<ViolationRow> found = new ArrayList<>();
        jdbc.query("""
                SELECT v.detected_at, z.name, v.violation_type, v.review_status, v.subject, u.employee_code, u.full_name,
                       r.full_name, v.dispute_status, v.user_id, z.team_id
                FROM violation v JOIN zone z ON z.id = v.zone_id
                         LEFT JOIN app_user u ON u.id = v.user_id
                         LEFT JOIN app_user r ON r.id = v.reviewed_by
                WHERE v.review_status IN ('CONFIRMED', 'REMINDER') AND v.detected_at >= ? AND v.detected_at < ?
                ORDER BY v.detected_at, v.id""", row -> {
            Long person = (Long) row.getObject(10);
            Long zoneTeam = (Long) row.getObject(11);
            boolean inScope = team == null || (person != null ? people.contains(person) : team.getId().equals(zoneTeam));
            if (inScope) {
                boolean stranger = "STRANGER".equals(row.getString(5));
                found.add(new ViolationRow(time(row.getTimestamp(1)), row.getString(2), row.getString(3),
                        stranger ? "STRANGER" : row.getString(4), row.getString(6), row.getString(7), row.getString(8),
                        row.getString(9)));
            }
        }, period.from(), period.to());
        return found;
    }

    private List<VisitRow> visits(Period period, Set<Long> people) {
        if (people.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT c.check_in_at, c.check_out_at, u.employee_code, u.full_name, s.start_at, s.end_at, c.method,
                       c.incomplete, c.auto_closed
                FROM check_in c JOIN app_user u ON u.id = c.user_id LEFT JOIN shift s ON s.id = c.shift_id
                WHERE c.user_id = ANY (?) AND c.check_in_at >= ? AND c.check_in_at < ?
                ORDER BY c.check_in_at, c.id""", (row, n) -> new VisitRow(time(row.getTimestamp(1)),
                time(row.getTimestamp(2)), row.getString(3), row.getString(4), time(row.getTimestamp(5)),
                time(row.getTimestamp(6)), row.getString(7), row.getBoolean(8), row.getBoolean(9)),
                people.toArray(Long[]::new), period.from(), period.to());
    }

    private Team scope(Long actorId, Long teamId) {
        if (access.has(actorId, Permissions.REPORT_EXPORT_ALL)) {
            return teamId == null ? null : teams.require(teamId);
        }
        access.require(actorId, Permissions.REPORT_EXPORT_TEAM);
        return teams.findAllByManagerId(actorId).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("You don't lead a team yet"));
    }

    /** MSG49: a month that has not started is refused. */
    private Period period(YearMonth month) {
        OffsetDateTime now = OffsetDateTime.now();
        if (month == null || month.isAfter(YearMonth.from(siteTime.today()))) {
            throw new BusinessRuleException("This month has not started yet.");
        }
        OffsetDateTime from = siteTime.startOf(month.atDay(1));
        OffsetDateTime end = siteTime.startOf(month.plusMonths(1).atDay(1));
        return new Period(month, from, end.isAfter(now) ? now : end);
    }

    private static OffsetDateTime time(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
