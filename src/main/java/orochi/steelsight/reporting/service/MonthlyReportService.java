package orochi.steelsight.reporting.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.attendance.service.VisitStatus;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.ViolationType;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.repository.TeamRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The month's figures accounting needs to work out pay and bonuses: safety score, violations and
 * attendance for each worker. A team leader gets the team they lead; an admin gets every worker.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MonthlyReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);

    private final AccessPolicy access;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final TeamRepository teams;
    private final CheckInService checkIns;
    private final JdbcTemplate jdbc;

    @Value("${steelsight.time-zone}")
    private String timeZone;

    /** to is the end of the month, or now for the month that is still running. */
    public record Period(YearMonth month, OffsetDateTime from, OffsetDateTime to) {
    }

    public record WorkerRow(Long id, String code, String name, String team, Integer score, String level,
                            int noHardHat, int noVest, int otherViolations, int awaitingDecision,
                            int shiftsScheduled, int shiftsWorked, int noShows, BigDecimal hoursWorked,
                            int timesLate, long minutesLate, int timesEarly, long minutesEarly, int missingCheckOuts) {
        public int violations() {
            return noHardHat + noVest + otherViolations;
        }
    }

    public record ViolationRow(OffsetDateTime detectedAt, String code, String name, ViolationType type,
                               String camera, DisputeStatus dispute) {
    }

    public record VisitRow(OffsetDateTime checkInAt, OffsetDateTime checkOutAt, String code, String name,
                           OffsetDateTime shiftStart, OffsetDateTime shiftEnd, long minutesLate, long minutesEarly,
                           boolean checkOutEstimated) {
    }

    public record NoShowRow(OffsetDateTime shiftStart, OffsetDateTime shiftEnd, String code, String name) {
    }

    /** team is null when the report covers every worker. */
    public record Report(Period period, String team, OffsetDateTime generatedAt, List<WorkerRow> workers,
                         List<ViolationRow> violations, List<VisitRow> visits, List<NoShowRow> noShows) {
    }

    private record ShiftRow(Long userId, OffsetDateTime start, OffsetDateTime end, boolean attended) {
    }

    public Report build(Long actorId, YearMonth month) {
        Scope scope = scopeOf(actorId);
        Period period = period(month);
        Map<Long, AppUser> workers = scope.workers().stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        Map<Long, Integer> scores = new HashMap<>();
        Map<Long, String> levels = new HashMap<>();
        // The function counts the whole calendar month that p_at falls in.
        jdbc.query("SELECT user_id, score, level FROM safety_scores(?)", row -> {
            scores.put(row.getLong("user_id"), row.getInt("score"));
            levels.put(row.getLong("user_id"), row.getString("level"));
        }, Timestamp.from(period.to().minusNanos(1000).toInstant()));

        List<ViolationRow> violations = new ArrayList<>();
        Map<Long, int[]> violationCounts = new HashMap<>();   // no hard hat, no vest, other, awaiting decision
        jdbc.query("""
                SELECT v.user_id, v.detected_at, v.violation_type, c.camera_code, v.dispute_status
                FROM violation v JOIN camera c ON c.id = v.camera_id
                WHERE v.user_id IS NOT NULL AND v.detected_at >= ? AND v.detected_at < ?
                ORDER BY v.detected_at, v.id
                """, row -> {
            AppUser worker = workers.get(row.getLong("user_id"));
            if (worker == null) {
                return;
            }
            ViolationType type = ViolationType.valueOf(row.getString("violation_type"));
            DisputeStatus dispute = DisputeStatus.valueOf(row.getString("dispute_status"));
            violations.add(new ViolationRow(time(row.getTimestamp("detected_at")), worker.getEmployeeCode(),
                    worker.getFullName(), type, row.getString("camera_code"), dispute));
            if (dispute == DisputeStatus.OVERTURNED) {
                return;
            }
            int[] counts = violationCounts.computeIfAbsent(worker.getId(), id -> new int[4]);
            counts[type == ViolationType.NO_HARD_HAT ? 0 : type == ViolationType.NO_VEST ? 1 : 2]++;
            if (dispute == DisputeStatus.DISPUTED) {
                counts[3]++;
            }
        }, Timestamp.from(period.from().toInstant()), Timestamp.from(period.to().toInstant()));

        // A visit counts for a shift when it is linked to it, or overlaps it: detection doesn't link them yet.
        List<ShiftRow> shifts = jdbc.query("""
                SELECT s.user_id, s.start_at, s.end_at,
                       EXISTS (SELECT 1 FROM check_in c
                               WHERE c.user_id = s.user_id
                                 AND (c.shift_id = s.id
                                      OR (c.check_in_at < s.end_at AND COALESCE(c.check_out_at, now()) > s.start_at))) AS attended
                FROM shift s
                WHERE s.status <> 'CANCELLED' AND s.start_at >= ? AND s.start_at < ?
                ORDER BY s.start_at, s.id
                """, (row, n) -> new ShiftRow(row.getLong("user_id"), time(row.getTimestamp("start_at")),
                        time(row.getTimestamp("end_at")), row.getBoolean("attended")),
                Timestamp.from(period.from().toInstant()), Timestamp.from(period.to().toInstant()))
                .stream().filter(s -> workers.containsKey(s.userId())).toList();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<NoShowRow> noShows = shifts.stream()
                .filter(s -> !s.attended() && !s.end().isAfter(now))
                .map(s -> new NoShowRow(s.start(), s.end(), workers.get(s.userId()).getEmployeeCode(),
                        workers.get(s.userId()).getFullName()))
                .toList();

        List<VisitStatus> visits = checkIns.listStartedInScope(actorId, period.from(), period.to()).stream()
                .filter(v -> workers.containsKey(v.visit().getUser().getId()))
                .sorted(Comparator.comparing((VisitStatus v) -> v.visit().getCheckInAt()))
                .toList();

        List<WorkerRow> rows = scope.workers().stream()
                .sorted(Comparator.comparing(AppUser::getFullName).thenComparing(AppUser::getId))
                .map(worker -> row(worker, scores, levels, violationCounts, shifts, noShows, visits, now))
                .toList();
        List<VisitRow> visitRows = visits.stream().map(this::visitRow).toList();

        log.info("Monthly report for {} built by user {} ({} workers)", month, actorId, rows.size());
        return new Report(period, scope.team() == null ? null : scope.team().getName(), now, rows,
                violations, visitRows, noShows);
    }

    private WorkerRow row(AppUser worker, Map<Long, Integer> scores, Map<Long, String> levels,
                          Map<Long, int[]> violationCounts, List<ShiftRow> shifts, List<NoShowRow> noShows,
                          List<VisitStatus> visits, OffsetDateTime now) {
        Long id = worker.getId();
        int[] counts = violationCounts.getOrDefault(id, new int[4]);
        List<ShiftRow> own = shifts.stream().filter(s -> s.userId().equals(id)).toList();
        List<VisitStatus> mine = visits.stream().filter(v -> v.visit().getUser().getId().equals(id)).toList();
        long workedSeconds = mine.stream()
                .filter(v -> v.visit().getCheckOutAt() != null)
                .mapToLong(v -> Duration.between(v.visit().getCheckInAt(), v.visit().getCheckOutAt()).getSeconds())
                .filter(seconds -> seconds > 0)
                .sum();
        return new WorkerRow(id, worker.getEmployeeCode(), worker.getFullName(),
                worker.getTeam() == null ? null : worker.getTeam().getName(),
                scores.get(id), levels.get(id),
                counts[0], counts[1], counts[2], counts[3],
                own.size(), (int) own.stream().filter(ShiftRow::attended).count(),
                (int) noShows.stream().filter(n -> n.code().equals(worker.getEmployeeCode())).count(),
                BigDecimal.valueOf(workedSeconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP),
                (int) mine.stream().filter(VisitStatus::isLate).count(),
                mine.stream().mapToLong(VisitStatus::minutesLate).sum(),
                (int) mine.stream().filter(VisitStatus::isEarly).count(),
                mine.stream().mapToLong(VisitStatus::minutesEarly).sum(),
                (int) mine.stream().filter(VisitStatus::isMissingCheckOut).count());
    }

    private VisitRow visitRow(VisitStatus status) {
        var visit = status.visit();
        var shift = visit.getShift();
        return new VisitRow(visit.getCheckInAt(), visit.getCheckOutAt(), visit.getUser().getEmployeeCode(),
                visit.getUser().getFullName(), shift == null ? null : shift.getStartAt(),
                shift == null ? null : shift.getEndAt(), status.minutesLate(), status.minutesEarly(),
                status.isMissingCheckOut());
    }

    private record Scope(Team team, List<AppUser> workers) {
    }

    /** A leader's own team, or every worker with REPORT_EXPORT_ALL. */
    private Scope scopeOf(Long actorId) {
        Long workerRole = roles.findByRoleName("WORKER").orElseThrow().getId();
        if (access.has(actorId, Permissions.REPORT_EXPORT_ALL)) {
            return new Scope(null, users.findAllByRoleIdAndDeletedAtIsNull(workerRole));
        }
        access.require(actorId, Permissions.REPORT_EXPORT_TEAM);
        Team team = teams.findAllByManagerId(actorId).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("You don't lead a team yet"));
        List<AppUser> members = users.findAllByTeamIdAndDeletedAtIsNull(team.getId()).stream()
                .filter(u -> u.getRole().getId().equals(workerRole))
                .toList();
        return new Scope(team, members);
    }

    private Period period(YearMonth month) {
        ZoneId zone = ZoneId.of(timeZone);
        OffsetDateTime now = OffsetDateTime.now(zone);
        if (month == null || month.isAfter(YearMonth.from(now))) {
            throw new BusinessRuleException("Choose a month that has already started");
        }
        OffsetDateTime from = month.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        return new Period(month, from, end.isAfter(now) ? now : end);
    }

    private OffsetDateTime time(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
}
