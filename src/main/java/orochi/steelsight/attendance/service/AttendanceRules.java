package orochi.steelsight.attendance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.model.Team;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Late arrival is made up at the end of the shift, up to the team's grace period: with 15 minutes on
 * an 08:00 to 17:00 shift, arriving at 08:10 means working until 17:10, and arriving at 08:15 or later
 * means working until 17:15. Arriving after the grace period is tagged late, and leaving before the
 * required time is tagged early. Times compare in whole minutes, as pages show them.
 */
@Component
public class AttendanceRules {

    private final Duration graceWithoutTeam;
    private final Duration minimumVisit;

    public AttendanceRules(@Value("${steelsight.attendance.late-after-minutes}") long minutesWithoutTeam,
                           @Value("${steelsight.attendance.minimum-visit-minutes}") long minimumVisitMinutes) {
        this.graceWithoutTeam = Duration.ofMinutes(minutesWithoutTeam);
        this.minimumVisit = Duration.ofMinutes(minimumVisitMinutes);
    }

    public VisitStatus assess(CheckIn visit) {
        Shift shift = visit.getShift();
        boolean tooShort = isTooShort(visit);
        if (shift == null || shift.getStatus() == ShiftStatus.CANCELLED) {
            return new VisitStatus(visit, null, 0, 0, tooShort);
        }
        Duration lateBy = positive(Duration.between(toMinute(shift.getStartAt()), toMinute(visit.getCheckInAt())));
        Duration grace = graceFor(visit);
        boolean late = lateBy.compareTo(grace) > 0;
        OffsetDateTime requiredCheckOut = toMinute(shift.getEndAt()).plus(late ? grace : lateBy);
        long minutesEarly = 0;
        if (visit.getCheckOutAt() != null && !visit.isAutoClosed()) {
            minutesEarly = positive(Duration.between(toMinute(visit.getCheckOutAt()), requiredCheckOut)).toMinutes();
        }
        return new VisitStatus(visit, requiredCheckOut, late ? lateBy.toMinutes() : 0, minutesEarly, tooShort);
    }

    /** An estimated check-out says nothing about how long the worker stayed, so only a recorded one can be too short. */
    private boolean isTooShort(CheckIn visit) {
        return visit.getCheckOutAt() != null && !visit.isAutoClosed()
                && Duration.between(visit.getCheckInAt(), visit.getCheckOutAt()).compareTo(minimumVisit) < 0;
    }

    private Duration graceFor(CheckIn visit) {
        Team team = visit.getUser().getTeam();
        return team == null ? graceWithoutTeam : Duration.ofMinutes(team.getLateAfterMinutes());
    }

    private Duration positive(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private OffsetDateTime toMinute(OffsetDateTime time) {
        return time.truncatedTo(ChronoUnit.MINUTES);
    }
}
