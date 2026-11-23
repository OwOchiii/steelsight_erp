package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.jobs.SiteJobs;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The main workflows of SRS 1.2 end to end, as the people in them click through the web ERP. The desktop
 * app is played by plain SQL inserts with the detection role's columns, as it writes them.
 */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class SrsWorkflowTest extends PageTestSupport {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired SiteJobs jobs;

    Long admin;
    Long hse;
    Long leader;
    Long worker;
    Long gateCamera;
    Long workCamera;
    Long workZone;

    @BeforeEach
    void seed() {
        admin = userWithRole("WF-A", "An Admin", "ADMIN");
        hse = userWithRole("WF-H", "Hoa Officer", "HSE_OFFICER");
        leader = userWithRole("WF-M", "Minh Leader", "MANAGER");
        worker = userWithRole("WF-W", "Wanda Worker", "WORKER");
        Long team = team("Workflow team", leader);
        joinTeam(worker, team);
        Long gate = zone("Gate");
        jdbc.update("UPDATE zone SET kind = 'GATE_ATTENDANCE' WHERE id = ?", gate);
        gateCamera = camera("WF-CAM01", gate);
        jdbc.update("UPDATE camera SET role = 'ATTENDANCE' WHERE id = ?", gateCamera);
        workZone = zone("Area A");
        jdbc.update("UPDATE zone SET team_id = ? WHERE id = ?", team, workZone);
        workCamera = camera("WF-CAM03", workZone);
    }

    /** 1.2.4: the gate records the check-in; the ERP attaches it to the shift and My Attendance shows it. */
    @Test
    void workflow124_aGateCheckInIsAttachedToTheShiftAndShownOnMyAttendance() throws Exception {
        Long shift = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '3 hours', now() + interval '1 hour', ?) RETURNING id""", Long.class, worker, workZone, leader);
        jdbc.update("INSERT INTO check_in (user_id, camera_id, check_in_at, face_similarity) VALUES (?, ?, now() - interval '3 hours 2 minutes', 91)",
                worker, gateCamera);

        assertThat(jdbc.queryForObject("SELECT shift_id FROM check_in WHERE user_id = ?", Long.class, worker)).isEqualTo(shift);
        mvc.perform(get("/attendance/mine").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("On site")))
                .andExpect(content().string(containsString("Provisional")));
        mvc.perform(get("/attendance/team").with(as(leader)))
                .andExpect(content().string(containsString("Wanda Worker (WF-W)")));
    }

    /**
     * 1.2.6 and 1.2.7: a camera records a violation, the HSE Officer is alerted, confirms the worker from
     * the zone's team, and the worker sees it with the new score.
     */
    @Test
    void workflow126_127_detectAlertConfirmAndTheWorkerSeesTheScore() throws Exception {
        Long violation = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, 'NO_HARD_HAT', now(), 'cam03.jpg') RETURNING id""", Long.class, workCamera);

        mvc.perform(get("/notifications").with(as(hse))).andExpect(content().string(containsString("Area A")));
        mvc.perform(get("/violations/" + violation).with(as(hse)))
                .andExpect(content().string(containsString("Team of this zone")))
                .andExpect(content().string(containsString("Wanda Worker (WF-W)")));
        mvc.perform(post("/violations/" + violation + "/confirm").param("personId", worker.toString()).param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("success", "The violation was confirmed."));

        mvc.perform(get("/violations").with(as(worker)))
                .andExpect(content().string(containsString(">95<")))
                .andExpect(content().string(containsString("Appeal until")));
        mvc.perform(get("/notifications").with(as(worker))).andExpect(content().string(containsString("Violation confirmed")));
    }

    /** 1.2.8: the worker appeals; the Admin asks the HSE Officer, writes the facts and overturns. */
    @Test
    void workflow128_anAppealIsDecidedByTheAdminWithTheHseFacts() throws Exception {
        Long violation = violation(worker, workCamera, "NO_VEST");

        mvc.perform(post("/violations/" + violation + "/dispute").param("reason", "I had my vest on, check the photo")
                        .with(as(worker)).with(csrf()))
                .andExpect(flash().attribute("success", "Your dispute was sent to the Admin."));
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE violation_id = ?", Long.class, violation);

        mvc.perform(post("/tickets/" + ticket + "/ask").param("officerId", hse.toString()).with(as(admin)).with(csrf()));
        mvc.perform(post("/tickets/" + ticket + "/comments").param("body", "The vest was under the jacket").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("success", "Your comment was added."));
        mvc.perform(post("/violations/" + violation + "/decision").param("decision", "OVERTURNED")
                        .param("hseFacts", "The officer says the vest was under the jacket.")
                        .param("note", "Overturned: your points are back.").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "Your decision was saved and the ticket is resolved."));

        assertThat(textColumn("SELECT status || '/' || outcome FROM ticket WHERE id = ?", ticket)).isEqualTo("RESOLVED/OVERTURNED");
        assertThat(jdbc.queryForObject("SELECT score FROM safety_scores(now()) WHERE user_id = ?", Integer.class, worker)).isEqualTo(100);
        mvc.perform(get("/tickets/" + ticket).with(as(worker))).andExpect(content().string(containsString("Overturned: your points are back.")));
    }

    /** 1.2.10: no check-out, flagged at 23:00; the worker explains; the Admin approves. */
    @Test
    void workflow1210_aMissingCheckOutIsFlaggedExplainedAndApproved() throws Exception {
        Long shift = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '6 hours', now() - interval '2 hours', ?) RETURNING id""", Long.class, worker, workZone, leader);
        jdbc.update("INSERT INTO check_in (user_id, camera_id, shift_id, check_in_at, face_similarity) VALUES (?, ?, ?, now() - interval '6 hours', 90)",
                worker, gateCamera, shift);
        jobs.dayEnd(LocalDateTime.now(VN));
        Long visit = jdbc.queryForObject("SELECT id FROM check_in WHERE user_id = ? AND incomplete", Long.class, worker);

        mvc.perform(get("/attendance/mine").with(as(worker)))
                .andExpect(content().string(containsString("Incomplete attendance")))
                .andExpect(content().string(containsString("category=MISSING_CHECK_OUT")));
        mvc.perform(post("/tickets").param("categoryId", category("Missing check-out").toString()).param("checkInId", visit.toString())
                        .param("subject", "Forgot to check out").param("description", "I left by the side gate at the shift end")
                        .with(as(worker)).with(csrf()))
                .andExpect(flash().attribute("success", "Your ticket was sent."));
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE check_in_id = ?", Long.class, visit);
        mvc.perform(post("/tickets/" + ticket + "/decide").param("outcome", "APPROVED").param("note", "Approved after the gate log check.")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The ticket was resolved."));

        assertThat(jdbc.queryForObject("SELECT NOT incomplete AND check_out_at IS NOT NULL FROM check_in WHERE id = ?", Boolean.class, visit)).isTrue();
    }

    /** 1.2.11: time after the shift becomes pending overtime; the Admin adds the overtime shift from the list. */
    @Test
    void workflow1211_pendingOvertimeShowsOnTheListAndOpensTheShiftForm() throws Exception {
        Long shift = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
                VALUES (?, ?, now() - interval '6 hours', now() - interval '3 hours', ?) RETURNING id""", Long.class, worker, workZone, leader);
        jdbc.update("""
                INSERT INTO check_in (user_id, shift_id, check_in_at, check_out_at, method, recorded_by)
                VALUES (?, ?, now() - interval '6 hours', now() - interval '1 hour', 'MANUAL', ?)""", worker, shift, hse);
        jobs.dayEnd(LocalDateTime.now(VN));
        Long pending = jdbc.queryForObject("SELECT id FROM pending_overtime WHERE user_id = ?", Long.class, worker);

        mvc.perform(get("/overtime").with(as(admin)))
                .andExpect(content().string(containsString("Wanda Worker (WF-W)")))
                .andExpect(content().string(containsString("/shifts/new?pendingOvertimeId=" + pending)));
        mvc.perform(get("/shifts/new").param("pendingOvertimeId", pending.toString()).with(as(admin)))
                .andExpect(content().string(containsString("value=\"OVERTIME\" selected")));
        mvc.perform(get("/overtime").with(as(leader))).andExpect(status().isForbidden());
    }

    /** 1.2.12: a leave request for a coming shift, approved by the Admin, read by the team leader. */
    @Test
    void workflow1212_aLeaveRequestIsSentReadByTheLeaderAndApproved() throws Exception {
        // Tomorrow's morning frame, 07:00 to 11:30; leave for the last two hours
        java.time.LocalDate tomorrow = java.time.LocalDate.now(VN).plusDays(1);
        Long shift = jdbc.queryForObject("""
                INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, ?, ?, ?) RETURNING id""", Long.class,
                worker, workZone, tomorrow.atTime(7, 0).atZone(VN).toOffsetDateTime(), tomorrow.atTime(11, 30).atZone(VN).toOffsetDateTime(), leader);
        String from = "09:30";
        String to = "11:30";

        mvc.perform(post("/tickets").param("categoryId", category("Leave request").toString()).param("shiftId", shift.toString())
                        .param("leaveType", "PAID").param("leaveFrom", from).param("leaveTo", to)
                        .param("subject", "Leave on Friday").param("description", "Family wedding in the afternoon")
                        .with(as(worker)).with(csrf()))
                .andExpect(flash().attribute("success", "Your ticket was sent."));
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE shift_id = ?", Long.class, shift);

        mvc.perform(get("/tickets").param("team", "true").with(as(leader))).andExpect(content().string(containsString("Leave on Friday")));
        mvc.perform(post("/tickets/" + ticket + "/decide").param("outcome", "APPROVED").param("note", "Approved, enjoy the wedding.")
                        .with(as(leader)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + ticket + "/decide").param("outcome", "APPROVED").param("note", "Approved, enjoy the wedding.")
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The ticket was resolved."));
        assertThat(textColumn("SELECT outcome FROM ticket WHERE id = ?", ticket)).isEqualTo("APPROVED");
    }

    /** 1.2.13: after the lock the score is frozen and the monthly file is no longer provisional. */
    @Test
    void workflow1213_theMonthLocksAndTheReportIsFinal() throws Exception {
        YearMonth last = YearMonth.now(VN).minusMonths(1);
        Long violation = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, 'NO_VEST', ?, 'e.jpg') RETURNING id""", Long.class, workCamera, last.atDay(10).atTime(9, 0).atZone(VN).toOffsetDateTime());
        confirmFor(violation, worker);

        jobs.lock(last);

        mvc.perform(post("/violations/" + violation + "/dispute").param("reason", "I wore it, please check")
                        .with(as(worker)).with(csrf()))
                .andExpect(redirectedUrl("/violations/" + violation));
        assertThat(jdbc.queryForObject("SELECT score FROM monthly_score WHERE user_id = ? AND month = ?", Integer.class, worker, last.atDay(1)))
                .isEqualTo(95);
        mvc.perform(get("/reports").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/violations").param("month", last.toString()).with(as(worker)))
                .andExpect(content().string(not(containsString("Provisional"))));
    }
}
