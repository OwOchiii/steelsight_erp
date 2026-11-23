package orochi.steelsight.srs;

import org.junit.jupiter.api.Test;
import orochi.steelsight.attendance.service.ShiftAttendance;
import orochi.steelsight.attendance.service.ShiftAttendance.Flag;
import orochi.steelsight.attendance.service.ShiftAttendance.Leave;
import orochi.steelsight.attendance.service.ShiftAttendance.PendingOvertime;
import orochi.steelsight.attendance.service.ShiftAttendance.Result;
import orochi.steelsight.attendance.service.ShiftAttendance.Shift;
import orochi.steelsight.attendance.service.ShiftAttendance.Visit;
import orochi.steelsight.ticketing.model.LeaveType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SRS 3.2.39, the attendance calculation every page and the report share. The fixture is the morning
 * frame 07:00 to 11:30 (BR-21) on a day long past, so "now" never matters unless a test says so.
 */
class ShiftAttendanceTest {

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);
    private static final OffsetDateTime NOW = at("2026-11-20T12:00");
    private static final Shift MORNING = new Shift(1, at("2026-11-02T07:00"), at("2026-11-02T11:30"), false);

    @Test
    void br31_upToFiveMinutesLateIsOnTimeAndPaidFromTheStart() {
        Result result = of(visit("07:05", "11:30"));

        assertThat(result.flags()).contains(Flag.ON_TIME).doesNotContain(Flag.LATE);
        assertThat(result.paidHours()).isEqualByComparingTo("4.50");
    }

    @Test
    void br31_laterIsLatePaidFromTheRealCheckInWithNoMakeUp() {
        Result result = of(visit("07:20", "11:30"));

        assertThat(result.flags()).contains(Flag.LATE);
        assertThat(result.minutesLate()).isEqualTo(20);
        assertThat(result.paidHours()).isEqualByComparingTo("4.17");   // 07:20 to 11:30
    }

    @Test
    void br30_timeBeforeTheShiftIsNotPaid() {
        Result result = of(visit("06:10", "11:30"));

        assertThat(result.paidHours()).isEqualByComparingTo("4.50");
        assertThat(result.flags()).contains(Flag.ON_TIME);
    }

    @Test
    void br32_aCheckOutBeforeTheEndIsEarlyAndPaidToTheCheckOut() {
        Result result = of(visit("07:00", "11:00"));

        assertThat(result.flags()).contains(Flag.EARLY);
        assertThat(result.minutesEarly()).isEqualTo(30);
        assertThat(result.paidHours()).isEqualByComparingTo("4.00");
    }

    @Test
    void br32_br68_approvedLeaveIsLeaveNotEarlyLeave() {
        Leave leave = new Leave(LeaveType.PAID, at("2026-11-02T10:30"), at("2026-11-02T11:30"));
        Result result = ShiftAttendance.of(MORNING, List.of(visit("07:00", "10:30")), List.of(leave), List.of(), NOW, d -> d, false);

        assertThat(result.flags()).contains(Flag.PAID_LEAVE).doesNotContain(Flag.EARLY);
        assertThat(result.paidLeaveHours()).isEqualByComparingTo("1.00");
        assertThat(result.paidHours()).isEqualByComparingTo("3.50");
    }

    @Test
    void br33_paidHoursSubtractApprovedLeaveInsideTheVisit() {
        Leave leave = new Leave(LeaveType.UNPAID, at("2026-11-02T09:00"), at("2026-11-02T10:00"));
        Result result = ShiftAttendance.of(MORNING, List.of(visit("07:00", "11:30")), List.of(leave), List.of(), NOW, d -> d, false);

        assertThat(result.paidHours()).isEqualByComparingTo("3.50");
        assertThat(result.unpaidLeaveHours()).isEqualByComparingTo("1.00");
    }

    @Test
    void br33_aVisitUnderTenMinutesIsIgnoredSoTheShiftIsAbsent() {
        Result result = of(visit("07:00", "07:09"));

        assertThat(result.flags()).contains(Flag.ABSENT);
        assertThat(result.paidHours()).isEqualByComparingTo("0");
    }

    @Test
    void br33_aShiftThatEndedWithNoVisitIsAbsent() {
        assertThat(of().flags()).containsExactly(Flag.ABSENT);
    }

    @Test
    void aShiftStillAheadIsUpcomingNotAbsent() {
        Result result = ShiftAttendance.of(MORNING, List.of(), List.of(), List.of(), at("2026-11-02T06:00"), d -> d, false);

        assertThat(result.flags()).containsExactly(Flag.UPCOMING);
    }

    @Test
    void br33_aWholeShiftOnApprovedLeaveIsNotAbsent() {
        Leave leave = new Leave(LeaveType.PAID, MORNING.start(), MORNING.end());
        Result result = ShiftAttendance.of(MORNING, List.of(), List.of(leave), List.of(), NOW, d -> d, false);

        assertThat(result.flags()).containsExactly(Flag.PAID_LEAVE);
    }

    @Test
    void br33_twoVisitsInOneShiftAddUp() {
        Result result = of(visit("07:00", "09:00"), visit("09:30", "11:30"));

        assertThat(result.paidHours()).isEqualByComparingTo("4.00");
    }

    @Test
    void br34_aCheckOutUpToTwentyMinutesLateAddsNothing() {
        Result result = of(visit("07:00", "11:50"));

        assertThat(result.paidHours()).isEqualByComparingTo("4.50");
        assertThat(ShiftAttendance.pendingOvertimeOf(MORNING.end(), at("2026-11-02T11:50"))).isNull();
    }

    @Test
    void br34_timeAfterTheEndPlusTwentyMinutesIsPendingOvertime() {
        assertThat(ShiftAttendance.pendingOvertimeOf(MORNING.end(), at("2026-11-02T12:50"))).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void br37_pendingOvertimeShowsItsHoursAndWhenItEnds() {
        PendingOvertime pending = new PendingOvertime("PENDING", at("2026-11-02T11:50"), at("2026-11-02T12:50"), at("2026-11-04T11:50"));
        Result result = ShiftAttendance.of(MORNING, List.of(visit("07:00", "12:50")), List.of(), List.of(pending), NOW, d -> d, false);

        assertThat(result.flags()).contains(Flag.PENDING_OT);
        assertThat(result.pendingOvertimeHours()).isEqualByComparingTo("1.00");
        assertThat(result.paidHours()).as("paid at 0% until valid").isEqualByComparingTo("4.50");
    }

    @Test
    void br37_cancelledPendingOvertimeIsShownAsCancelled() {
        PendingOvertime cancelled = new PendingOvertime("CANCELLED", at("2026-11-02T11:50"), at("2026-11-02T12:50"), at("2026-11-04T11:50"));
        Result result = ShiftAttendance.of(MORNING, List.of(visit("07:00", "12:50")), List.of(), List.of(cancelled), NOW, d -> d, false);

        assertThat(result.flags()).contains(Flag.CANCELLED_OT).doesNotContain(Flag.PENDING_OT);
        assertThat(result.pendingOvertimeHours()).isEqualByComparingTo("0");
    }

    @Test
    void br38_anOvertimeShiftCountsTheOverlapAsValidOvertime() {
        Shift evening = new Shift(2, at("2026-11-02T18:00"), at("2026-11-02T22:00"), true);
        Result result = ShiftAttendance.of(evening, List.of(visit("17:45", "21:30")), List.of(), List.of(), NOW, d -> d, false);

        assertThat(result.flags()).contains(Flag.VALID_OT);
        assertThat(result.validOvertimeHours()).isEqualByComparingTo("3.50");
        assertThat(result.paidHours()).isEqualByComparingTo("0");
    }

    @Test
    void br35_anIncompleteVisitCountsZeroUntilExplained() {
        Visit incomplete = new Visit(9, at("2026-11-02T07:00"), at("2026-11-02T15:30"), true, true, at("2026-11-02T23:00"), false);
        Result result = ShiftAttendance.of(MORNING, List.of(incomplete), List.of(), List.of(), at("2026-11-03T10:00"),
                flagged -> flagged.plusDays(3), false);

        assertThat(result.flags()).contains(Flag.INCOMPLETE).doesNotContain(Flag.HALF_DAY, Flag.TO_REVIEW);
        assertThat(result.paidHours()).isEqualByComparingTo("0");
    }

    @Test
    void br36_whenTheExplanationPeriodEndsTheShiftCountsHalfWithTheSettingOn() {
        Visit incomplete = new Visit(9, at("2026-11-02T07:00"), null, false, true, at("2026-11-02T23:00"), false);
        Result result = ShiftAttendance.of(MORNING, List.of(incomplete), List.of(), List.of(), NOW, flagged -> flagged.plusDays(3), true);

        assertThat(result.flags()).contains(Flag.INCOMPLETE, Flag.HALF_DAY);
        assertThat(result.paidHours()).isEqualByComparingTo("2.25");
    }

    @Test
    void br36_withTheSettingOffItCountsZeroAndIsToReview() {
        Visit incomplete = new Visit(9, at("2026-11-02T07:00"), null, false, true, at("2026-11-02T23:00"), false);
        Result result = ShiftAttendance.of(MORNING, List.of(incomplete), List.of(), List.of(), NOW, flagged -> flagged.plusDays(3), false);

        assertThat(result.flags()).contains(Flag.INCOMPLETE, Flag.TO_REVIEW);
        assertThat(result.paidHours()).isEqualByComparingTo("0");
    }

    @Test
    void someoneStillOnSiteIsPaidSoFarAndNotEarly() {
        Result result = ShiftAttendance.of(MORNING, List.of(new Visit(9, at("2026-11-02T07:00"), null, false, false, null, false)),
                List.of(), List.of(), at("2026-11-02T09:00"), d -> d, false);

        assertThat(result.flags()).contains(Flag.ON_SITE).doesNotContain(Flag.EARLY, Flag.ON_TIME);
        assertThat(result.paidHours()).isEqualByComparingTo("2.00");
    }

    @Test
    void br39_aVisitEditedByHandIsMarked() {
        Result result = of(new Visit(9, at("2026-11-02T07:00"), at("2026-11-02T11:30"), false, false, null, true));

        assertThat(result.flags()).contains(Flag.EDITED);
    }

    @Test
    void theSummaryAddsUpTheMonth() {
        Shift evening = new Shift(2, at("2026-11-02T18:00"), at("2026-11-02T22:00"), true);
        ShiftAttendance.Summary summary = ShiftAttendance.Summary.of(List.of(
                of(visit("07:20", "11:00")),
                ShiftAttendance.of(MORNING, List.of(), List.of(), List.of(), NOW, d -> d, false),
                ShiftAttendance.of(evening, List.of(visit("18:00", "20:00")), List.of(), List.of(), NOW, d -> d, false)));

        assertThat(summary.planned()).isEqualTo(2);
        assertThat(summary.worked()).isEqualTo(1);
        assertThat(summary.absent()).isEqualTo(1);
        assertThat(summary.timesLate()).isEqualTo(1);
        assertThat(summary.minutesLate()).isEqualTo(20);
        assertThat(summary.timesEarly()).isEqualTo(1);
        assertThat(summary.minutesEarly()).isEqualTo(30);
        assertThat(summary.validOvertimeHours()).isEqualByComparingTo("2.00");
    }

    private static Result of(Visit... visits) {
        return ShiftAttendance.of(MORNING, List.of(visits), List.of(), List.of(), NOW, d -> d, false);
    }

    private static Visit visit(String in, String out) {
        return new Visit(9, at("2026-11-02T" + in), at("2026-11-02T" + out), false, false, null, false);
    }

    private static OffsetDateTime at(String local) {
        return java.time.LocalDateTime.parse(local).atOffset(VN);
    }
}
