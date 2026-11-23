package orochi.steelsight.attendance.service;

import org.springframework.stereotype.Component;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * How one visit went against its shift, for the lists that show visits rather than shifts (SRS BR-31,
 * BR-32): a check-in up to 5 minutes after the start is on time; later is late, with no make-up time at
 * the end. A check-out before the shift end is early. Times compare in whole minutes, as pages show them.
 * Paid hours come from ShiftAttendance, which uses the same rules.
 */
@Component
public class AttendanceRules {

    public VisitStatus assess(CheckIn visit) {
        Shift shift = visit.getShift();
        boolean tooShort = isTooShort(visit);
        if (shift == null || shift.getStatus() == ShiftStatus.CANCELLED) {
            return new VisitStatus(visit, null, 0, 0, tooShort);
        }
        Duration lateBy = positive(Duration.between(toMinute(shift.getStartAt()), toMinute(visit.getCheckInAt())));
        boolean late = lateBy.compareTo(ShiftAttendance.GRACE) > 0;
        OffsetDateTime requiredCheckOut = toMinute(shift.getEndAt());
        long minutesEarly = 0;
        if (visit.getCheckOutAt() != null && !visit.isAutoClosed()) {
            minutesEarly = positive(Duration.between(toMinute(visit.getCheckOutAt()), requiredCheckOut)).toMinutes();
        }
        return new VisitStatus(visit, requiredCheckOut, late ? lateBy.toMinutes() : 0, minutesEarly, tooShort);
    }

    /** BR-33: a recorded visit under 10 minutes is ignored. One closed by the system has no real length. */
    private boolean isTooShort(CheckIn visit) {
        return visit.getCheckOutAt() != null && !visit.isAutoClosed()
                && Duration.between(visit.getCheckInAt(), visit.getCheckOutAt()).compareTo(ShiftAttendance.MINIMUM_VISIT) < 0;
    }

    private Duration positive(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private OffsetDateTime toMinute(OffsetDateTime time) {
        return time.truncatedTo(ChronoUnit.MINUTES);
    }
}
