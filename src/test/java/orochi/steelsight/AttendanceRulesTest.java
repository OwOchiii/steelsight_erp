package orochi.steelsight;

import org.junit.jupiter.api.Test;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.service.AttendanceRules;
import orochi.steelsight.attendance.service.VisitStatus;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.model.Team;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The 08:00 to 17:00 shift from the requirement, for a worker with no team: 15 minutes before a late tag. */
class AttendanceRulesTest {

    private final AttendanceRules rules = new AttendanceRules(15);

    @Test
    void arrivingAndLeavingOnTimeHasNoTags() {
        VisitStatus status = assess("08:00", "17:00");

        assertThat(status.isOnTime()).isTrue();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:00"));
    }

    @Test
    void arrivingEarlyDoesNotAllowLeavingEarly() {
        VisitStatus status = assess("07:30", "16:30");

        assertThat(status.isLate()).isFalse();
        assertThat(status.minutesEarly()).isEqualTo(30);
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:00"));
    }

    @Test
    void comingAt0815MeansWorkingUntil1715WithoutALateTag() {
        VisitStatus status = assess("08:15", "17:15");

        assertThat(status.isLate()).isFalse();
        assertThat(status.isEarly()).isFalse();
        assertThat(status.isOnTime()).isTrue();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:15"));
    }

    @Test
    void leavingAtTheUsualTimeAfterComing15MinutesLateIsEarly() {
        VisitStatus status = assess("08:15", "17:00");

        assertThat(status.isLate()).isFalse();
        assertThat(status.minutesEarly()).isEqualTo(15);
    }

    @Test
    void lateTimeWithinTheGracePeriodIsMadeUpMinuteForMinute() {
        VisitStatus status = assess("08:10", "17:10");

        assertThat(status.isOnTime()).isTrue();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:10"));
    }

    @Test
    void theRequiredCheckOutStopsAtTheGracePeriodHoweverLateTheArrival() {
        VisitStatus status = assess("09:00", "17:15");

        assertThat(status.minutesLate()).isEqualTo(60);
        assertThat(status.isEarly()).isFalse();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:15"));
    }

    @Test
    void oneMinutePastTheGracePeriodIsLate() {
        VisitStatus status = assess("08:16", "17:15");

        assertThat(status.minutesLate()).isEqualTo(16);
        assertThat(status.isEarly()).isFalse();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:15"));
    }

    @Test
    void someoneTaggedLateWhoLeavesBeforeTheRequiredTimeIsAlsoEarly() {
        VisitStatus status = assess("09:00", "17:00");

        assertThat(status.minutesLate()).isEqualTo(60);
        assertThat(status.minutesEarly()).isEqualTo(15);
    }

    @Test
    void theWorkersTeamSetsTheGracePeriod() {
        Team relaxed = new Team();
        relaxed.setLateAfterMinutes(30);
        Team strict = new Team();
        strict.setLateAfterMinutes(0);

        CheckIn visit = visit(at("08:20"), at("17:20"), shift());
        visit.getUser().setTeam(relaxed);
        assertThat(rules.assess(visit).isLate()).isFalse();

        visit.getUser().setTeam(strict);
        assertThat(rules.assess(visit).minutesLate()).isEqualTo(20);
    }

    @Test
    void secondsDoNotCount() {
        VisitStatus status = rules.assess(visit(at("08:15").plusSeconds(40), at("17:15").plusSeconds(10), shift()));

        assertThat(status.isLate()).isFalse();
        assertThat(status.isEarly()).isFalse();
    }

    @Test
    void someoneStillOnSiteIsNotEarlyYetButCanBeLate() {
        VisitStatus status = assess("08:30", null);

        assertThat(status.isOnSite()).isTrue();
        assertThat(status.isEarly()).isFalse();
        assertThat(status.isLate()).isTrue();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:15"));
        assertThat(status.isOnTime()).isFalse();
    }

    @Test
    void anEstimatedCheckOutIsNotJudgedAsEarly() {
        CheckIn visit = visit(at("08:30"), at("17:00"), shift());
        visit.setAutoClosed(true);

        VisitStatus status = rules.assess(visit);

        assertThat(status.isMissingCheckOut()).isTrue();
        assertThat(status.isEarly()).isFalse();
        assertThat(status.isOnTime()).isFalse();
    }

    @Test
    void aVisitWithoutAShiftOrWithACancelledOneHasNoTags() {
        Shift cancelled = shift();
        cancelled.setStatus(ShiftStatus.CANCELLED);

        for (Shift shift : new Shift[]{null, cancelled}) {
            VisitStatus status = rules.assess(visit(at("12:00"), at("13:00"), shift));
            assertThat(status.hasShift()).isFalse();
            assertThat(status.isLate()).isFalse();
            assertThat(status.isEarly()).isFalse();
            assertThat(status.isOnTime()).isFalse();
        }
    }

    private VisitStatus assess(String in, String out) {
        return rules.assess(visit(at(in), out == null ? null : at(out), shift()));
    }

    private CheckIn visit(OffsetDateTime in, OffsetDateTime out, Shift shift) {
        CheckIn visit = new CheckIn();
        visit.setUser(new AppUser());
        visit.setCheckInAt(in);
        visit.setCheckOutAt(out);
        visit.setShift(shift);
        return visit;
    }

    private Shift shift() {
        Shift shift = new Shift();
        shift.setStartAt(at("08:00"));
        shift.setEndAt(at("17:00"));
        return shift;
    }

    private OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-11-02T" + time + ":00+07:00");
    }
}
