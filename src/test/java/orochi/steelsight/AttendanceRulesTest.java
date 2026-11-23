package orochi.steelsight;

import org.junit.jupiter.api.Test;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.CheckInMethod;
import orochi.steelsight.attendance.service.AttendanceRules;
import orochi.steelsight.attendance.service.VisitStatus;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.ppe.model.PpeLinking;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.model.Team;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SRS BR-31, BR-32, BR-33 for one visit: a 5-minute grace with no make-up time, early leave against the
 * shift end, and the 10-minute minimum visit. The 08:00 to 17:00 shift is the fixture.
 */
class AttendanceRulesTest {

    private final AttendanceRules rules = new AttendanceRules();

    @Test
    void arrivingAndLeavingOnTimeHasNoTags() {
        VisitStatus status = assess("08:00", "17:00");

        assertThat(status.isOnTime()).isTrue();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:00"));
    }

    @Test
    void br31_upToFiveMinutesLateIsOnTime() {
        assertThat(assess("08:05", "17:00").isLate()).isFalse();
        assertThat(assess("08:05", "17:00").isOnTime()).isTrue();
    }

    @Test
    void br31_sixMinutesLateIsLateWithTheWholeMinutesRecorded() {
        VisitStatus status = assess("08:06", "17:00");

        assertThat(status.isLate()).isTrue();
        assertThat(status.minutesLate()).isEqualTo(6);
    }

    @Test
    void br31_thereIsNoMakeUpTimeAtTheEnd() {
        VisitStatus status = assess("09:00", "17:00");

        assertThat(status.minutesLate()).isEqualTo(60);
        assertThat(status.isEarly()).isFalse();
        assertThat(status.requiredCheckOut()).isEqualTo(at("17:00"));
    }

    @Test
    void br32_leavingBeforeTheShiftEndIsEarly() {
        VisitStatus status = assess("07:30", "16:30");

        assertThat(status.isLate()).isFalse();
        assertThat(status.minutesEarly()).isEqualTo(30);
    }

    @Test
    void theTeamNoLongerChangesTheGrace() {
        Team relaxed = new Team();
        relaxed.setLateAfterMinutes(30);
        CheckIn visit = visit(at("08:20"), at("17:00"), shift());
        visit.getUser().setTeam(relaxed);

        assertThat(rules.assess(visit).minutesLate()).isEqualTo(20);
    }

    @Test
    void secondsDoNotCount() {
        VisitStatus status = rules.assess(visit(at("08:05").plusSeconds(40), at("17:00").plusSeconds(10), shift()));

        assertThat(status.isLate()).isFalse();
        assertThat(status.isEarly()).isFalse();
    }

    @Test
    void someoneStillOnSiteIsNotEarlyYetButCanBeLate() {
        VisitStatus status = assess("08:30", null);

        assertThat(status.isOnSite()).isTrue();
        assertThat(status.isEarly()).isFalse();
        assertThat(status.isLate()).isTrue();
        assertThat(status.isOnTime()).isFalse();
    }

    @Test
    void aVisitClosedByTheSystemIsNotJudgedAsEarly() {
        CheckIn visit = visit(at("08:00"), at("21:00"), shift());
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

    @Test
    void br33_aRecordedVisitUnderTenMinutesIsTooShort() {
        assertThat(assess("08:00", "08:09").tooShort()).isTrue();
        assertThat(assess("08:00", "08:10").tooShort()).isFalse();
    }

    @Test
    void aVisitStillOpenOrClosedByTheSystemIsNotTooShort() {
        assertThat(assess("08:00", null).tooShort()).isFalse();

        CheckIn closed = visit(at("08:00"), at("08:01"), shift());
        closed.setAutoClosed(true);
        assertThat(rules.assess(closed).tooShort()).isFalse();
    }

    @Test
    void br35_anIncompleteVisitNeedsReview() {
        CheckIn visit = visit(at("08:00"), null, shift());
        visit.flagIncomplete(at("23:00"));

        assertThat(rules.assess(visit).isNeedsReview()).isTrue();
        assertThat(assess("08:00", "17:00").isNeedsReview()).isFalse();
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
