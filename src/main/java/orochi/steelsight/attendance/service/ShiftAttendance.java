package orochi.steelsight.attendance.service;

import orochi.steelsight.ticketing.model.LeaveType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The attendance of one shift, worked out with the rules of SRS 3.2.39 so that My Attendance, Team
 * Attendance, the rosters and the monthly report always agree. Pure: everything it needs is passed in.
 *
 * <ul>
 *   <li>BR-31: a check-in up to 5 minutes after the start is on time and paid from the start; later is
 *       late, paid from the real check-in. No make-up time at the end.</li>
 *   <li>BR-32: a check-out before the end without approved leave is early leave, paid to the check-out.</li>
 *   <li>BR-33: paid hours = overlap of the visits with the shift after those rules, minus approved leave,
 *       2 decimals. A visit under 10 minutes is ignored; a shift that ended with no visit is Absent.</li>
 *   <li>BR-34: time after the end adds nothing; pending overtime is kept separately.</li>
 *   <li>BR-35, BR-36: an Incomplete visit counts 0 hours until explained; when the explanation period
 *       ends it counts half the shift if the company setting says so, otherwise 0 and To review.</li>
 *   <li>BR-38: an OVERTIME shift's hours are valid overtime = overlap of the visits with it.</li>
 * </ul>
 */
public final class ShiftAttendance {

    public static final Duration GRACE = Duration.ofMinutes(5);
    public static final Duration MINIMUM_VISIT = Duration.ofMinutes(10);
    public static final Duration BUFFER = Duration.ofMinutes(20);

    public enum Flag {
        ON_TIME, LATE, EARLY, ABSENT, ON_SITE, INCOMPLETE, HALF_DAY, TO_REVIEW, PAID_LEAVE, UNPAID_LEAVE,
        EDITED, PENDING_OT, VALID_OT, CANCELLED_OT, UPCOMING
    }

    public record Shift(long id, OffsetDateTime start, OffsetDateTime end, boolean overtime) {
        Duration length() {
            return Duration.between(start, end);
        }
    }

    /** One visit of the person. out is null while on site. */
    public record Visit(long id, OffsetDateTime in, OffsetDateTime out, boolean closedBySystem, boolean incomplete,
                        OffsetDateTime incompleteAt, boolean edited) {
    }

    public record Leave(LeaveType type, OffsetDateTime from, OffsetDateTime to) {
    }

    public record PendingOvertime(String status, OffsetDateTime start, OffsetDateTime end, OffsetDateTime expiresAt) {
        BigDecimal hours() {
            return hoursOf(Duration.between(start, end));
        }
    }

    public record Result(Shift shift, Set<Flag> flags, BigDecimal paidHours, BigDecimal validOvertimeHours,
                         long minutesLate, long minutesEarly, BigDecimal paidLeaveHours, BigDecimal unpaidLeaveHours,
                         BigDecimal pendingOvertimeHours, OffsetDateTime pendingOvertimeEnds, List<Visit> visits) {

        public boolean has(Flag flag) {
            return flags.contains(flag);
        }

        public boolean isWorked() {
            return !visits.isEmpty();
        }
    }

    private ShiftAttendance() {
    }

    /**
     * @param explanationEnds when the explanation period of an Incomplete visit ends (BR-67)
     * @param halfDayForUnexplained the company setting of BR-36
     */
    public static Result of(Shift shift, List<Visit> allVisits, List<Leave> leaves, List<PendingOvertime> pending,
                            OffsetDateTime now, Function<OffsetDateTime, OffsetDateTime> explanationEnds,
                            boolean halfDayForUnexplained) {
        Set<Flag> flags = EnumSet.noneOf(Flag.class);
        List<Visit> visits = allVisits.stream().filter(ShiftAttendance::counts).toList();
        BigDecimal paidLeave = BigDecimal.ZERO;
        BigDecimal unpaidLeave = BigDecimal.ZERO;
        Duration leaveTotal = Duration.ZERO;
        for (Leave leave : leaves) {
            Duration length = overlap(leave.from(), leave.to(), shift.start(), shift.end());
            leaveTotal = leaveTotal.plus(length);
            if (leave.type() == LeaveType.PAID) {
                paidLeave = paidLeave.add(hoursOf(length));
                flags.add(Flag.PAID_LEAVE);
            } else {
                unpaidLeave = unpaidLeave.add(hoursOf(length));
                flags.add(Flag.UNPAID_LEAVE);
            }
        }
        BigDecimal pendingHours = BigDecimal.ZERO;
        OffsetDateTime pendingEnds = null;
        for (PendingOvertime overtime : pending) {
            switch (overtime.status()) {
                case "PENDING" -> {
                    flags.add(Flag.PENDING_OT);
                    pendingHours = pendingHours.add(overtime.hours());
                    pendingEnds = overtime.expiresAt();
                }
                case "CANCELLED" -> flags.add(Flag.CANCELLED_OT);
                default -> { }
            }
        }
        if (visits.stream().anyMatch(Visit::edited)) {
            flags.add(Flag.EDITED);
        }

        if (visits.isEmpty()) {
            if (shift.end().isAfter(now)) {
                flags.add(Flag.UPCOMING);
            } else if (leaveTotal.compareTo(shift.length()) < 0) {
                flags.add(Flag.ABSENT);
            }
            return new Result(shift, flags, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0, 0,
                    paidLeave, unpaidLeave, pendingHours, pendingEnds, visits);
        }

        if (shift.overtime()) {
            Duration worked = Duration.ZERO;
            for (Visit visit : visits) {
                if (!visit.incomplete()) {
                    worked = worked.plus(overlap(visit.in(), outOrNow(visit, now), shift.start(), shift.end()));
                }
            }
            flags.add(Flag.VALID_OT);
            if (visits.stream().anyMatch(v -> v.out() == null && !v.incomplete())) {
                flags.add(Flag.ON_SITE);
            }
            return new Result(shift, flags, BigDecimal.ZERO.setScale(2), hoursOf(worked), 0, 0,
                    paidLeave, unpaidLeave, pendingHours, pendingEnds, visits);
        }

        Visit unexplained = visits.stream().filter(Visit::incomplete).findFirst().orElse(null);
        if (unexplained != null) {
            flags.add(Flag.INCOMPLETE);
            OffsetDateTime ends = unexplained.incompleteAt() == null ? now : explanationEnds.apply(unexplained.incompleteAt());
            BigDecimal paid = BigDecimal.ZERO.setScale(2);
            if (now.isAfter(ends)) {
                if (halfDayForUnexplained) {
                    flags.add(Flag.HALF_DAY);
                    paid = hoursOf(shift.length().dividedBy(2));
                } else {
                    flags.add(Flag.TO_REVIEW);
                }
            }
            return new Result(shift, flags, paid, BigDecimal.ZERO.setScale(2), 0, 0,
                    paidLeave, unpaidLeave, pendingHours, pendingEnds, visits);
        }

        OffsetDateTime firstIn = visits.stream().map(Visit::in).min(OffsetDateTime::compareTo).orElseThrow();
        boolean onSite = visits.stream().anyMatch(v -> v.out() == null);
        long minutesLate = 0;
        OffsetDateTime paidFrom = shift.start();
        if (firstIn.isAfter(shift.start().plus(GRACE))) {
            minutesLate = Duration.between(shift.start(), firstIn).toMinutes();
            paidFrom = firstIn;
            flags.add(Flag.LATE);
        }
        long minutesEarly = 0;
        if (onSite) {
            flags.add(Flag.ON_SITE);
        } else {
            OffsetDateTime lastOut = visits.stream().map(Visit::out).max(OffsetDateTime::compareTo).orElseThrow();
            if (lastOut.isBefore(shift.end())) {
                Duration gap = Duration.between(lastOut, shift.end());
                for (Leave leave : leaves) {
                    gap = gap.minus(overlap(leave.from(), leave.to(), lastOut, shift.end()));
                }
                minutesEarly = Math.max(0, gap.toMinutes());
                if (minutesEarly > 0) {
                    flags.add(Flag.EARLY);
                }
            }
        }
        Duration paid = Duration.ZERO;
        for (Visit visit : visits) {
            OffsetDateTime out = outOrNow(visit, now);
            // BR-31: within the grace the first visit is paid from the shift start
            OffsetDateTime in = minutesLate == 0 && visit.in().equals(firstIn) && visit.in().isAfter(shift.start())
                    ? shift.start() : visit.in();
            paid = paid.plus(overlap(in, out, paidFrom, shift.end()));
            for (Leave leave : leaves) {
                Duration both = overlap(visit.in(), out, paidFrom, shift.end());
                if (!both.isZero()) {
                    paid = paid.minus(overlap(leave.from(), leave.to(),
                            max(visit.in(), paidFrom), min(out, shift.end())));
                }
            }
        }
        if (minutesLate == 0 && minutesEarly == 0 && !onSite) {
            flags.add(Flag.ON_TIME);
        }
        return new Result(shift, flags, hoursOf(paid), BigDecimal.ZERO.setScale(2), minutesLate, minutesEarly,
                paidLeave, unpaidLeave, pendingHours, pendingEnds, visits);
    }

    /** BR-33: a recorded visit under 10 minutes is ignored. A visit closed by the system has no real length. */
    static boolean counts(Visit visit) {
        return visit.out() == null || visit.closedBySystem() || visit.incomplete()
                || Duration.between(visit.in(), visit.out()).compareTo(MINIMUM_VISIT) >= 0;
    }

    /** BR-34: the part of a visit after the shift end plus 20 minutes, or null. */
    public static Duration pendingOvertimeOf(OffsetDateTime shiftEnd, OffsetDateTime checkOut) {
        OffsetDateTime from = shiftEnd.plus(BUFFER);
        return checkOut != null && checkOut.isAfter(from) ? Duration.between(from, checkOut) : null;
    }

    public static BigDecimal hoursOf(Duration duration) {
        return BigDecimal.valueOf(Math.max(0, duration.getSeconds()))
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    static Duration overlap(OffsetDateTime aFrom, OffsetDateTime aTo, OffsetDateTime bFrom, OffsetDateTime bTo) {
        OffsetDateTime from = max(aFrom, bFrom);
        OffsetDateTime to = min(aTo, bTo);
        return to.isAfter(from) ? Duration.between(from, to) : Duration.ZERO;
    }

    private static OffsetDateTime outOrNow(Visit visit, OffsetDateTime now) {
        return visit.out() != null ? visit.out() : now;
    }

    private static OffsetDateTime max(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static OffsetDateTime min(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    /** Totals of a month (SRS 3.2.12 summary, and the report). */
    public record Summary(int planned, int worked, int absent, BigDecimal paidHours, BigDecimal validOvertimeHours,
                          BigDecimal pendingOvertimeHours, int timesLate, long minutesLate, int timesEarly,
                          long minutesEarly, int incomplete, BigDecimal paidLeaveHours, BigDecimal unpaidLeaveHours) {

        public static Summary of(List<Result> results) {
            List<Result> regular = new ArrayList<>();
            BigDecimal ot = BigDecimal.ZERO;
            for (Result result : results) {
                if (result.shift().overtime()) {
                    ot = ot.add(result.validOvertimeHours());
                } else {
                    regular.add(result);
                }
            }
            return new Summary(regular.size(),
                    (int) regular.stream().filter(Result::isWorked).count(),
                    (int) regular.stream().filter(r -> r.has(Flag.ABSENT)).count(),
                    regular.stream().map(Result::paidHours).reduce(BigDecimal.ZERO, BigDecimal::add),
                    ot,
                    results.stream().map(Result::pendingOvertimeHours).reduce(BigDecimal.ZERO, BigDecimal::add),
                    (int) regular.stream().filter(r -> r.has(Flag.LATE)).count(),
                    regular.stream().mapToLong(Result::minutesLate).sum(),
                    (int) regular.stream().filter(r -> r.has(Flag.EARLY)).count(),
                    regular.stream().mapToLong(Result::minutesEarly).sum(),
                    (int) regular.stream().filter(r -> r.has(Flag.INCOMPLETE)).count(),
                    results.stream().map(Result::paidLeaveHours).reduce(BigDecimal.ZERO, BigDecimal::add),
                    results.stream().map(Result::unpaidLeaveHours).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }
}
