package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.jobs.JobScheduler;
import orochi.steelsight.jobs.SiteJobs;
import orochi.steelsight.scheduling.model.ShiftKind;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.scheduling.service.ShiftService.ShiftDetails;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SRS workflow 1.2.3 and 1.2.11 and the time-based jobs: shift rules BR-18 to BR-24, pending overtime
 * BR-34 and BR-37, the incomplete flag BR-35, overdue violations BR-53 and the month lock BR-70.
 */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class SrsShiftsAndJobsTest extends PageTestSupport {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired ShiftService shifts;
    @Autowired SiteJobs jobs;
    @Autowired JobScheduler scheduler;

    Long admin;
    Long leader;
    Long worker;
    Long outsider;
    Long zone;
    /** Monday two weeks ahead: its schedule hasn't been sent (BR-23). */
    LocalDate openMonday = LocalDate.now(VN).plusWeeks(2).with(DayOfWeek.MONDAY);

    @BeforeEach
    void seed() {
        admin = userWithRole("SJ-A", "An Admin", "ADMIN");
        leader = userWithRole("SJ-M", "Minh Leader", "MANAGER");
        worker = userWithRole("SJ-W", "Wanda Worker", "WORKER");
        outsider = userWithRole("SJ-O", "Otto Outsider", "WORKER");
        joinTeam(worker, team("Shift team", leader));
        joinTeam(outsider, team("Other team", userWithRole("SJ-M2", "Mai Leader", "MANAGER")));
        zone = zone("Shift zone");
    }

    // --- shifts ---------------------------------------------------------------------------------

    /** BR-21, MSG26: 30 minutes to 12 hours, starting and ending on the same day. */
    @Test
    void br21_aShiftLastsThirtyMinutesToTwelveHoursOnOneDay() {
        String msg26 = "The end must be after the start and the shift must last 30 minutes to 12 hours.";
        assertThatThrownBy(() -> shifts.create(leader, regular(worker, openMonday, 7, 0, 7, 20)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(msg26);
        assertThatThrownBy(() -> shifts.create(admin, regular(worker, openMonday, 6, 0, 18, 30)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(msg26);
        assertThatThrownBy(() -> shifts.create(admin, new ShiftDetails(worker, zone, at(openMonday, 20, 0), at(openMonday.plusDays(1), 2, 0))))
                .isInstanceOf(BusinessRuleException.class).hasMessage(msg26);

        assertThat(shifts.create(leader, regular(worker, openMonday, 7, 0, 11, 30)).getKind()).isEqualTo(ShiftKind.REGULAR);
    }

    /** BR-22, MSG27: a person's shifts never overlap. */
    @Test
    void br22_overlappingShiftsAreRefusedWithTheirTimes() {
        shifts.create(leader, regular(worker, openMonday, 7, 0, 11, 30));

        assertThatThrownBy(() -> shifts.create(leader, regular(worker, openMonday, 11, 0, 13, 0)))
                .isInstanceOf(BusinessRuleException.class).hasMessageStartingWith("This person already has a shift from");
    }

    /** BR-23, MSG29: only the Admin plans overtime, always with a reason. */
    @Test
    void br23_onlyTheAdminPlansOvertimeWithAReason() {
        ShiftDetails noReason = new ShiftDetails(worker, zone, at(openMonday, 18, 0), at(openMonday, 22, 0), ShiftKind.OVERTIME, null, null);
        ShiftDetails withReason = new ShiftDetails(worker, zone, at(openMonday, 18, 0), at(openMonday, 22, 0), ShiftKind.OVERTIME,
                "Concrete pour must finish tonight", null);

        assertThatThrownBy(() -> shifts.create(leader, withReason))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Only the Admin plans overtime shifts");
        assertThatThrownBy(() -> shifts.create(admin, noReason))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Write the reason for the overtime (10 to 500 characters).");
        assertThat(shifts.create(admin, withReason).isOvertime()).isTrue();
    }

    /** BR-23: a team leader plans only his team, and only before the week's schedule is sent. */
    @Test
    void br23_aLeaderPlansOnlyHisTeamAndOnlyBeforeTheScheduleIsSent() {
        assertThatThrownBy(() -> shifts.create(leader, regular(outsider, openMonday, 7, 0, 11, 30)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        LocalDate sent = LocalDate.now(VN).plusDays(1);   // tomorrow's week is sent from the Saturday before, or is this week
        if (!shifts.isOpenForManagers(sent)) {
            assertThatThrownBy(() -> shifts.create(leader, regular(worker, sent, 7, 0, 11, 30)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("The schedule of this week has been sent. Only the Admin can change it now.");
        }
        assertThat(shifts.publishedAt(openMonday)).isEqualTo(at(openMonday.minusDays(2), 18, 0));
    }

    /** BR-24, MSG28: a shift that has started can't be added, edited or cancelled. */
    @Test
    void br24_aStartedShiftCannotBeAddedEditedOrCancelled() {
        OffsetDateTime started = OffsetDateTime.now().minusMinutes(30);
        assertThatThrownBy(() -> shifts.create(admin, new ShiftDetails(worker, zone, started, started.plusHours(2))))
                .isInstanceOf(BusinessRuleException.class).hasMessage("This shift has already started and cannot be changed.");

        Long running = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '30 minutes', now() + interval '2 hours', ?) RETURNING id""", Long.class, worker, zone, admin);
        assertThatThrownBy(() -> shifts.cancel(admin, running))
                .isInstanceOf(BusinessRuleException.class).hasMessage("This shift has already started and cannot be changed.");
    }

    /** BR-19: a change to a shift of the current or next week tells the person at once; later weeks wait for Saturday. */
    @Test
    void br19_onlyShiftsOfTheCurrentOrNextWeekSendAChangeNotice() {
        LocalDate nextWeek = LocalDate.now(VN).plusWeeks(1).with(DayOfWeek.WEDNESDAY);
        shifts.create(admin, regular(worker, nextWeek, 7, 0, 11, 30));
        shifts.create(admin, regular(worker, openMonday.plusWeeks(1), 7, 0, 11, 30));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND kind = 'SHIFT_ADDED'", Integer.class, worker))
                .isEqualTo(1);
    }

    /** SRS 3.2.9: Copy last week copies the regular shifts it may plan and lists what it skipped. */
    @Test
    void copyLastWeekCopiesRegularShiftsAndListsWhatItSkipped() {
        shifts.create(leader, regular(worker, openMonday, 7, 0, 11, 30));
        shifts.create(leader, regular(worker, openMonday.plusDays(1), 13, 30, 17, 30));
        shifts.create(admin, regular(worker, openMonday.plusWeeks(1).plusDays(1), 13, 0, 15, 0));   // will clash

        ShiftService.CopyResult result = shifts.copyLastWeek(leader, openMonday.plusWeeks(1));

        assertThat(result.copied()).isEqualTo(1);
        assertThat(result.skipped()).hasSize(1).first().asString().contains("already has a shift");
    }

    // --- pending overtime (BR-34, BR-37) --------------------------------------------------------

    /** BR-34: at 23:00 the time after the shift end plus 20 minutes becomes pending overtime. */
    @Test
    void br34_dayEndTurnsLateCheckOutsIntoPendingOvertime() {
        Long shift = pastShift(5, 2);
        visit(shift, "now() - interval '5 hours'", "now() - interval '1 hour'");   // left 60 minutes after the end

        jobs.dayEnd(LocalDateTime.now(VN));

        assertThat(jdbc.queryForObject("""
                SELECT round(extract(epoch FROM end_at - start_at) / 60) FROM pending_overtime WHERE user_id = ?""", Integer.class, worker))
                .isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT expires_at <= now() + interval '48 hours' FROM pending_overtime WHERE user_id = ?",
                Boolean.class, worker)).isTrue();
    }

    @Test
    void br34_aCheckOutWithinTwentyMinutesIsNotOvertime() {
        Long shift = pastShift(5, 2);
        visit(shift, "now() - interval '5 hours'", "now() - interval '1 hour 45 minutes'");

        jobs.dayEnd(LocalDateTime.now(VN));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pending_overtime", Integer.class)).isZero();
    }

    /** BR-37: an overtime shift added within 48 hours makes it valid; after that it is cancelled for good (MSG34). */
    @Test
    void br37_anOvertimeShiftMakesItValidButNotOnceCancelled() {
        LocalDate yesterday = LocalDate.now(VN).minusDays(1);
        Long pending = pendingOvertime(yesterday);
        ShiftDetails overtime = new ShiftDetails(worker, zone, at(yesterday, 11, 50), at(yesterday, 13, 30), ShiftKind.OVERTIME,
                "Stayed to finish the formwork", pending);

        shifts.create(admin, overtime);

        assertThat(textColumn("SELECT status FROM pending_overtime WHERE id = ?", pending)).isEqualTo("VALID");

        LocalDate earlier = yesterday.minusDays(1);
        Long late = pendingOvertime(earlier);
        jdbc.update("UPDATE pending_overtime SET expires_at = now() - interval '1 minute' WHERE id = ?", late);
        assertThat(jobs.hourly(LocalDateTime.now(VN))).contains("expired=1");
        assertThatThrownBy(() -> shifts.create(admin, new ShiftDetails(worker, zone, at(earlier, 11, 50), at(earlier, 13, 30),
                ShiftKind.OVERTIME, "Stayed to finish the formwork", late)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This pending overtime was cancelled and can no longer be made valid.");
    }

    // --- jobs -----------------------------------------------------------------------------------

    /** BR-35: 23:00 flags a visit with no check-out and tells the person. */
    @Test
    void br35_dayEndFlagsIncompleteAttendanceAndTellsThePerson() {
        Long shift = pastShift(5, 1);
        Long visit = visit(shift, "now() - interval '5 hours'", null);

        jobs.dayEnd(LocalDateTime.now(VN));

        assertThat(jdbc.queryForObject("SELECT incomplete FROM check_in WHERE id = ?", Boolean.class, visit)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND kind = 'INCOMPLETE_ATTENDANCE'",
                Integer.class, worker)).isEqualTo(1);
    }

    /** SRS 3.2.38: a job never runs twice for the same time (RE-08). */
    @Test
    void aJobRunsOnceForEachScheduledTime() {
        LocalDateTime due = LocalDateTime.now(VN).withSecond(0).withNano(0);

        assertThat(scheduler.runOnce("morning", due)).isNotNull();
        assertThat(scheduler.runOnce("morning", due)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_run WHERE job = 'morning'", Integer.class)).isEqualTo(1);
    }

    /** BR-18: on Saturday at 18:00 everyone gets next week's shifts, or "no shift". */
    @Test
    void br18_theWeeklyScheduleReachesEveryActiveEmployee() {
        LocalDate saturday = openMonday.minusDays(2);
        shifts.create(leader, regular(worker, openMonday, 7, 0, 11, 30));

        jobs.weeklySchedule(saturday.atTime(18, 0));

        assertThat(jdbc.queryForObject("SELECT kind FROM notification WHERE user_id = ? AND kind LIKE 'WEEKLY%'", String.class, worker))
                .isEqualTo("WEEKLY_SCHEDULE");
        assertThat(jdbc.queryForObject("SELECT kind FROM notification WHERE user_id = ? AND kind LIKE 'WEEKLY%'", String.class, outsider))
                .isEqualTo("WEEKLY_EMPTY");
    }

    /** SRS 3.2.38: at 07:00 the Admin gets the overdue violations and the pending overtime. */
    @Test
    void theMorningListsGoToTheAdminAndTheTeamLeader() {
        pendingOvertime(LocalDate.now(VN).minusDays(1));
        Long camera = camera("SJ-CAM", zone);
        jdbc.update("INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path, review_status) VALUES (?, 'NO_VEST', now(), 'e.jpg', 'OVERDUE')", camera);

        jobs.morning(LocalDateTime.now(VN));

        assertThat(jdbc.queryForList("SELECT kind FROM notification WHERE user_id = ? AND kind IN ('OVERDUE_VIOLATIONS', 'PENDING_OVERTIME_LIST')",
                String.class, admin)).containsExactlyInAnyOrder("OVERDUE_VIOLATIONS", "PENDING_OVERTIME_LIST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND kind = 'PENDING_OVERTIME_LIST'",
                Integer.class, leader)).isEqualTo(1);
    }

    /** Ended shifts become COMPLETED by themselves (hourly and at 23:00); a locked month is left alone (BR-70). */
    @Test
    void endedShiftsAreCompletedAutomatically() {
        Long ended = pastShift(9, 1);
        Long running = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '2 hours', now() + interval '1 hour', ?) RETURNING id""", Long.class, outsider, zone, admin);
        Long cancelled = pastShift(30, 26);
        jdbc.update("UPDATE shift SET status = 'CANCELLED' WHERE id = ?", cancelled);
        Long locked = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, ?, ?, ?) RETURNING id""",
                Long.class, worker, zone, at(LocalDate.of(2026, 9, 10), 7, 0), at(LocalDate.of(2026, 9, 10), 15, 0), admin);
        jdbc.update("INSERT INTO month_lock (month) VALUES ('2026-09-01') ON CONFLICT DO NOTHING");
        long versionBefore = jdbc.queryForObject("SELECT version FROM shift WHERE id = ?", Long.class, ended);

        assertThat(jobs.hourly(LocalDateTime.now(VN))).contains("completed=1");

        assertThat(textColumn("SELECT status FROM shift WHERE id = ?", ended)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT version FROM shift WHERE id = ?", Long.class, ended)).isEqualTo(versionBefore + 1);
        assertThat(textColumn("SELECT status FROM shift WHERE id = ?", running)).isEqualTo("SCHEDULED");
        assertThat(textColumn("SELECT status FROM shift WHERE id = ?", cancelled)).isEqualTo("CANCELLED");
        assertThat(textColumn("SELECT status FROM shift WHERE id = ?", locked)).isEqualTo("SCHEDULED");

        Long other = userWithRole("SJ-W2", "Vy Worker", "WORKER");        // shifts of one person can't overlap
        Long laterToday = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '5 hours', now() - interval '1 minute', ?) RETURNING id""", Long.class, other, zone, admin);
        assertThat(jobs.dayEnd(LocalDateTime.now(VN))).contains("completed=1");
        assertThat(textColumn("SELECT status FROM shift WHERE id = ?", laterToday)).isEqualTo("COMPLETED");
    }

    /** BR-70: the lock job locks last month on the 2nd working day at 23:59. */
    @Test
    void br70_theLockJobLocksLastMonth() {
        jobs.lockMonth(LocalDateTime.of(2026, 10, 2, 23, 59));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_lock WHERE month = '2026-09-01'", Integer.class)).isEqualTo(1);
    }

    private ShiftDetails regular(Long who, LocalDate day, int fromHour, int fromMinute, int toHour, int toMinute) {
        return new ShiftDetails(who, zone, at(day, fromHour, fromMinute), at(day, toHour, toMinute));
    }

    private static OffsetDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(VN).toOffsetDateTime();
    }

    /** A regular shift that started and ended this many hours ago. */
    private Long pastShift(int startedHoursAgo, int endedHoursAgo) {
        return jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - make_interval(hours => ?), now() - make_interval(hours => ?), ?) RETURNING id""",
                Long.class, worker, zone, startedHoursAgo, endedHoursAgo, admin);
    }

    private Long visit(Long shift, String in, String out) {
        return jdbc.queryForObject("INSERT INTO check_in (user_id, shift_id, check_in_at, check_out_at, method, recorded_by) VALUES (?, ?, "
                + in + ", " + (out == null ? "NULL" : out) + ", 'MANUAL', ?) RETURNING id", Long.class, worker, shift, admin);
    }

    /** The morning frame of that day, left at 13:30: pending overtime 11:50 to 13:30, not expired yet. */
    private Long pendingOvertime(LocalDate day) {
        Long shift = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, ?, ?, ?) RETURNING id""",
                Long.class, worker, zone, at(day, 7, 0), at(day, 11, 30), admin);
        Long visit = jdbc.queryForObject("""
                INSERT INTO check_in (user_id, shift_id, check_in_at, check_out_at, method, recorded_by)
                SELECT ?, id, start_at, end_at + interval '2 hours', 'MANUAL', ? FROM shift WHERE id = ? RETURNING id""",
                Long.class, worker, admin, shift);
        return jdbc.queryForObject("""
                INSERT INTO pending_overtime (check_in_id, user_id, shift_id, start_at, end_at, expires_at)
                SELECT c.id, c.user_id, s.id, s.end_at + interval '20 minutes', c.check_out_at, now() + interval '40 hours'
                FROM check_in c JOIN shift s ON s.id = c.shift_id WHERE c.id = ? RETURNING id""", Long.class, visit);
    }
}
