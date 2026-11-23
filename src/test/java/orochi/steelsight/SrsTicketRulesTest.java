package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.service.AttendanceCalculator;
import orochi.steelsight.attendance.service.ShiftAttendance;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.ticketing.model.Direction;
import orochi.steelsight.ticketing.model.LeaveType;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketOutcome;
import orochi.steelsight.ticketing.service.TicketService;
import orochi.steelsight.ticketing.service.TicketService.NewTicket;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SRS workflows 1.2.8 to 1.2.12 and the ticket rules BR-61 to BR-69 (MSG38 to MSG47): appeals, attendance
 * errors, missing check-out explanations, leave requests, cancel, Ask HSE Officer.
 */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class SrsTicketRulesTest extends PageTestSupport {

    static final String FACTS = "The officer on duty saw him at the gate at 07:02.";
    static final String NOTE = "Checked with the gate log and the officer.";

    @Autowired TicketService tickets;
    @Autowired ViolationService violations;
    @Autowired AttendanceCalculator calculator;

    Long worker;
    Long rightPerson;
    Long hse;
    Long otherHse;
    Long admin;
    Long otherAdmin;
    Long zone;
    Long camera;

    @BeforeEach
    void seed() {
        worker = userWithRole("ST-W1", "Wanda Worker", "WORKER");
        rightPerson = userWithRole("ST-W2", "Rita Right", "WORKER");
        hse = userWithRole("ST-H1", "Hoa Officer", "HSE_OFFICER");
        otherHse = userWithRole("ST-H2", "Hung Officer", "HSE_OFFICER");
        admin = userWithRole("ST-A1", "An Admin", "ADMIN");
        otherAdmin = userWithRole("ST-A2", "Bao Admin", "ADMIN");
        zone = zone("Ticket zone");
        camera = camera("ST-CAM", zone);
    }

    // --- appeals (BR-64, BR-65) ----------------------------------------------------------------

    @Test
    void br64_anAppealWithinThreeWorkingDaysDisputesTheViolationAndKeepsThePoints() {
        Long id = violation(worker, camera, "NO_VEST");

        tickets.openDispute(worker, id, null, "I wore my vest the whole time");

        assertThat(textColumn("SELECT dispute_status FROM violation WHERE id = ?", id)).isEqualTo("DISPUTED");
        assertThat(score(worker)).as("the points stay until the decision").isEqualTo(95);
    }

    @Test
    void msg38_anAppealAfterThreeWorkingDaysIsRefused() {
        Long id = violation(worker, camera, "NO_VEST");
        jdbc.update("UPDATE violation SET reviewed_at = now() - interval '8 days' WHERE id = ?", id);

        assertThatThrownBy(() -> tickets.openDispute(worker, id, null, "I wore my vest the whole time"))
                .isInstanceOf(BusinessRuleException.class).hasMessage("The appeal period has ended.");
    }

    @Test
    void br64_anAppealOfALockedMonthIsRefused() {
        Long id = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, 'NO_VEST', timestamptz '2026-09-10 09:00+07', 'e.jpg') RETURNING id""", Long.class, camera);
        confirmFor(id, worker);
        jdbc.update("INSERT INTO month_lock (month) VALUES ('2026-09-01')");

        assertThatThrownBy(() -> tickets.openDispute(worker, id, null, "I wore my vest the whole time"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("locked");
    }

    @Test
    void msg39_aViolationIsAppealedOnlyOnce() {
        Long id = violation(worker, camera, "NO_VEST");
        Ticket first = tickets.openDispute(worker, id, null, "I wore my vest the whole time");
        tickets.decideAppeal(admin, id, DisputeStatus.UPHELD, FACTS, NOTE, null);

        assertThatThrownBy(() -> tickets.openDispute(worker, id, null, "Again, I wore my vest"))
                .isInstanceOf(BusinessRuleException.class).hasMessage("This violation has already been appealed.");
        assertThat(first.getOutcome()).isEqualTo(TicketOutcome.UPHELD);
    }

    @Test
    void br65_upheldKeepsThePointsAndOverturnedGivesThemBack() {
        Long upheld = violation(worker, camera, "NO_VEST");
        tickets.openDispute(worker, upheld, null, "I wore my vest the whole time");
        tickets.decideAppeal(admin, upheld, DisputeStatus.UPHELD, FACTS, NOTE, null);
        assertThat(score(worker)).isEqualTo(95);

        Long overturned = violation(worker, camera, "NO_HARD_HAT");
        tickets.openDispute(worker, overturned, null, "I wore my hard hat the whole time");
        tickets.decideAppeal(admin, overturned, DisputeStatus.OVERTURNED, FACTS, NOTE, null);
        assertThat(score(worker)).isEqualTo(95);
    }

    /** BR-65: overturned with the right person named, the violation goes back to the queue for them. */
    @Test
    void br65_overturnedWithTheRightPersonGoesBackToTheQueueForThem() {
        Long id = violation(worker, camera, "NO_VEST");
        tickets.openDispute(worker, id, null, "That was Rita, not me");

        tickets.decideAppeal(admin, id, DisputeStatus.OVERTURNED, FACTS, NOTE, rightPerson);
        flush();

        assertThat(textColumn("SELECT review_status FROM violation WHERE id = ?", id)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("SELECT suggested_user_id FROM violation WHERE id = ?", Long.class, id)).isEqualTo(rightPerson);
        assertThat(jdbc.queryForObject("SELECT user_id FROM violation WHERE id = ?", Long.class, id)).isNull();
        assertThat(score(worker)).isEqualTo(100);
    }

    @Test
    void br65_msg47_anAppealDecisionNeedsTheHseFactsAndANote() {
        Long id = violation(worker, camera, "NO_VEST");
        tickets.openDispute(worker, id, null, "I wore my vest the whole time");

        assertThatThrownBy(() -> tickets.decideAppeal(admin, id, DisputeStatus.UPHELD, null, NOTE, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("HSE Officer of that shift");
        assertThatThrownBy(() -> tickets.decideAppeal(admin, id, DisputeStatus.UPHELD, FACTS, "short", null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Write a note for the employee (10 to 500 characters).");
    }

    // --- attendance error (BR-66) --------------------------------------------------------------

    @Test
    void msg41_anAttendanceErrorNeedsAPhotoOfTheGateScreen() {
        Long shift = pastShift(worker, 1);

        assertThatThrownBy(() -> tickets.create(worker, attendanceError(shift, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Attach a photo taken at the gate.");
        assertThat(tickets.create(worker, attendanceError(shift, "gate.jpg")).getShift().getId()).isEqualTo(shift);
    }

    @Test
    void msg42_anAttendanceErrorAfterFortyEightHoursIsRefused() {
        Long shift = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '54 hours', now() - interval '49 hours', ?) RETURNING id""", Long.class, worker, zone, admin);

        assertThatThrownBy(() -> tickets.create(worker, attendanceError(shift, "gate.jpg")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("The period for this request has ended.");
    }

    /** SRS 3.2.22: approving an attendance error opens the Attendance Form; saving it resolves the ticket. */
    @Test
    void br66_approvingAnAttendanceErrorGoesThroughTheAttendanceForm() throws Exception {
        Long shift = pastShift(worker, 1);
        Long ticket = tickets.create(worker, attendanceError(shift, "gate.jpg")).getId();

        mvc.perform(post("/tickets/" + ticket + "/decide").param("outcome", "APPROVED").param("note", NOTE).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "Write what the HSE Officer of that shift said (10 to 1,000 characters)"));
        mvc.perform(post("/tickets/" + ticket + "/facts").param("hseFacts", FACTS).with(as(admin)).with(csrf()));
        mvc.perform(post("/tickets/" + ticket + "/decide").param("outcome", "APPROVED").param("note", NOTE).with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/attendance/new?ticket=" + ticket));
        mvc.perform(get("/attendance/new").param("ticket", ticket.toString()).with(as(admin)))
                .andExpect(content().string(containsString("This change answers ticket #" + ticket)));

        mvc.perform(post("/attendance").param("workerId", worker.toString())
                        .param("checkInAt", local(shift, "start_at")).param("checkOutAt", local(shift, "end_at"))
                        .param("note", "Gate camera missed the check-in").param("ticketId", ticket.toString())
                        .with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/tickets/" + ticket));

        assertThat(textColumn("SELECT status || '/' || outcome FROM ticket WHERE id = ?", ticket)).isEqualTo("RESOLVED/APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM check_in WHERE user_id = ? AND method = 'MANUAL'", Integer.class, worker)).isEqualTo(1);
    }

    // --- missing check-out (BR-67) -------------------------------------------------------------

    @Test
    void br67_onlyAnIncompleteVisitCanBeExplained() {
        Long shift = pastShift(worker, 1);
        Long complete = visit(worker, shift, false);

        assertThatThrownBy(() -> tickets.create(worker, explanation(complete)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Only a visit with incomplete attendance can be explained");
    }

    @Test
    void br67_anApprovedExplanationSetsTheCheckOutToTheShiftEndAndClearsTheFlag() {
        Long shift = pastShift(worker, 1);
        Long visit = visit(worker, shift, true);
        Long ticket = tickets.create(worker, explanation(visit)).getId();

        tickets.decide(admin, ticket, TicketOutcome.APPROVED, NOTE);
        flush();

        assertThat(jdbc.queryForObject("""
                SELECT c.check_out_at = s.end_at AND NOT c.incomplete AND NOT c.auto_closed
                FROM check_in c JOIN shift s ON s.id = c.shift_id WHERE c.id = ?""", Boolean.class, visit)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM check_in_correction WHERE check_in_id = ? AND ticket_id = ?",
                Integer.class, visit, ticket)).as("the history names the ticket").isEqualTo(1);
    }

    @Test
    void msg42_anExplanationAfterThreeWorkingDaysIsRefused() {
        Long shift = pastShift(worker, 1);
        Long visit = visit(worker, shift, true);
        jdbc.update("UPDATE check_in SET incomplete_at = now() - interval '8 days' WHERE id = ?", visit);

        assertThatThrownBy(() -> tickets.create(worker, explanation(visit)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("The period for this request has ended.");
    }

    // --- leave (BR-68) -------------------------------------------------------------------------

    @Test
    void msg43_leaveMustBeInsideTheShift() {
        Long shift = futureShift(worker);
        OffsetDateTime start = time(shift, "start_at");

        assertThatThrownBy(() -> tickets.create(worker, leave(shift, start.minusMinutes(30), start.plusHours(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inside the shift");
        assertThatThrownBy(() -> tickets.create(worker, leave(shift, start.plusHours(2), start.plusHours(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inside the shift");
    }

    @Test
    void msg44_leaveMustBeSentBeforeTheShiftEnds() {
        Long shift = pastShift(worker, 1);
        OffsetDateTime start = time(shift, "start_at");

        assertThatThrownBy(() -> tickets.create(worker, leave(shift, start, start.plusHours(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A leave request must be sent before the shift ends.");
    }

    /** BR-68, BR-32: approved leave is leave, not early leave. */
    @Test
    void br68_approvedLeaveIsCountedAsLeaveNotEarlyLeave() {
        Long shift = futureShift(worker);
        OffsetDateTime end = time(shift, "end_at");
        Long ticket = tickets.create(worker, leave(shift, end.minusHours(1), end)).getId();
        tickets.decide(admin, ticket, TicketOutcome.APPROVED, NOTE);
        // The shift then happens: worked from the start, left an hour early as agreed
        jdbc.update("UPDATE shift SET start_at = now() - interval '5 hours', end_at = now() - interval '1 hour' WHERE id = ?", shift);
        jdbc.update("UPDATE ticket SET leave_from = now() - interval '2 hours', leave_to = now() - interval '1 hour' WHERE id = ?", ticket);
        jdbc.update("""
                INSERT INTO check_in (user_id, shift_id, check_in_at, check_out_at, method, recorded_by)
                VALUES (?, ?, now() - interval '5 hours', now() - interval '2 hours', 'MANUAL', ?)""", worker, shift, admin);

        ShiftAttendance.Result result = calculator.rows(List.of(worker), OffsetDateTime.now().minusDays(1), OffsetDateTime.now()).get(0).result();

        assertThat(result.flags()).contains(ShiftAttendance.Flag.PAID_LEAVE).doesNotContain(ShiftAttendance.Flag.EARLY);
        assertThat(result.paidLeaveHours()).isEqualByComparingTo("1.00");
    }

    @Test
    void msg45_aSecondOpenRequestForTheSameShiftIsRefused() {
        Long shift = futureShift(worker);
        OffsetDateTime start = time(shift, "start_at");
        tickets.create(worker, leave(shift, start, start.plusHours(1)));

        assertThatThrownBy(() -> tickets.create(worker, leave(shift, start.plusHours(1), start.plusHours(2))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You already have an open request for this. Open it to add more details.");
    }

    // --- handling (BR-61, BR-62, BR-69) --------------------------------------------------------

    /** BR-62: only the creator cancels, and only while OPEN. */
    @Test
    void br62_theCreatorCancelsAnOpenTicketOnly() {
        Long first = tickets.create(worker, NewTicket.other(category("Other"), "New gloves", "My gloves are torn")).getId();
        Long second = tickets.create(worker, NewTicket.other(category("Other"), "New boots", "My boots are torn")).getId();

        assertThatThrownBy(() -> tickets.cancel(rightPerson, first)).isInstanceOf(AccessDeniedException.class);
        assertThat(tickets.cancel(worker, first).getStatus().name()).isEqualTo("CANCELLED");

        tickets.assign(admin, second, admin);
        assertThatThrownBy(() -> tickets.cancel(worker, second))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Only an open ticket can be cancelled");
    }

    /** BR-61: every active Admin is told of a new ticket, never the creator. */
    @Test
    void br61_everyAdminIsToldOfANewTicket() {
        tickets.create(worker, NewTicket.other(category("Other"), "New gloves", "My gloves are torn"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE kind = 'TICKET_CREATED' AND user_id IN (?, ?)",
                Integer.class, admin, otherAdmin)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE kind = 'TICKET_CREATED' AND user_id = ?",
                Integer.class, hse)).isZero();
    }

    /** SRS 3.2.22 #4: an HSE Officer the Admin asks may read and comment; another may not. */
    @Test
    void anAskedHseOfficerReadsAndComments() throws Exception {
        Long ticket = tickets.create(worker, NewTicket.other(category("Other"), "Wrong record", "My check-in on Monday is wrong")).getId();

        mvc.perform(get("/tickets/" + ticket).with(as(hse))).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + ticket + "/ask").param("officerId", hse.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The HSE Officer was asked to comment."));

        mvc.perform(get("/tickets/" + ticket).with(as(hse))).andExpect(status().isOk());
        mvc.perform(post("/tickets/" + ticket + "/comments").param("body", "I saw him at 07:02").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("success", "Your comment was added."));
        mvc.perform(get("/tickets/" + ticket).with(as(otherHse))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND kind = 'ASKED_FOR_FACTS'", Integer.class, hse))
                .isEqualTo(1);
    }

    /** BR-62: the creator is told of the decision with the note. */
    @Test
    void br62_theCreatorIsToldTheDecision() {
        Long ticket = tickets.create(worker, NewTicket.other(category("Other"), "New gloves", "My gloves are torn")).getId();

        tickets.decide(admin, ticket, TicketOutcome.REJECTED, NOTE);

        assertThat(jdbc.queryForObject("SELECT params->>'outcome' FROM notification WHERE user_id = ? AND kind = 'TICKET_DECIDED'",
                String.class, worker)).isEqualTo("REJECTED");
    }

    /** BR-69: the queue shows the age in working days; Sunday doesn't count. */
    @Test
    void br69_theAgeCountsWorkingDaysOnly() {
        Long ticket = tickets.create(worker, NewTicket.other(category("Other"), "New gloves", "My gloves are torn")).getId();
        jdbc.update("UPDATE ticket SET created_at = now() - interval '7 days' WHERE id = ?", ticket);
        flush();
        em.clear();

        assertThat(tickets.ageInWorkingDays(tickets.get(admin, ticket))).isEqualTo(6);
    }

    // --- helpers -------------------------------------------------------------------------------

    private NewTicket attendanceError(Long shift, String photo) {
        return new NewTicket(category("Attendance error"), "Missing check-in", "The gate did not record my check-in",
                photo, shift, null, Direction.IN, time(shift, "start_at"), null, null, null);
    }

    private NewTicket explanation(Long visit) {
        return new NewTicket(category("Missing check-out"), "Forgot to check out", "I left by the side gate at the shift end",
                null, null, visit, null, null, null, null, null);
    }

    private NewTicket leave(Long shift, OffsetDateTime from, OffsetDateTime to) {
        return new NewTicket(category("Leave request"), "Leave on Friday", "Family wedding in the afternoon",
                null, shift, null, null, null, LeaveType.PAID, from, to);
    }

    /** A 4-hour shift that ended this many hours ago. */
    private Long pastShift(Long user, int endedHoursAgo) {
        return jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - make_interval(hours => ?), now() - make_interval(hours => ?), ?) RETURNING id""",
                Long.class, user, zone, endedHoursAgo + 4, endedHoursAgo, admin);
    }

    private Long futureShift(Long user) {
        return jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() + interval '1 hour', now() + interval '5 hours', ?) RETURNING id""", Long.class, user, zone, admin);
    }

    private Long visit(Long user, Long shift, boolean incomplete) {
        return jdbc.queryForObject("""
                INSERT INTO check_in (user_id, shift_id, check_in_at, check_out_at, method, recorded_by, incomplete, incomplete_at)
                SELECT ?, s.id, s.start_at, CASE WHEN ? THEN NULL ELSE s.end_at END, 'MANUAL', ?, ?, CASE WHEN ? THEN now() END
                FROM shift s WHERE s.id = ? RETURNING id""", Long.class, user, incomplete, admin, incomplete, incomplete, shift);
    }

    private OffsetDateTime time(Long shift, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM shift WHERE id = ?", OffsetDateTime.class, shift);
    }

    private String local(Long shift, String column) {
        return textColumn("SELECT to_char(" + column + " AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD\"T\"HH24:MI') FROM shift WHERE id = ?", shift);
    }

    private Integer score(Long user) {
        return jdbc.queryForObject("SELECT score FROM safety_scores(now()) WHERE user_id = ?", Integer.class, user);
    }
}
