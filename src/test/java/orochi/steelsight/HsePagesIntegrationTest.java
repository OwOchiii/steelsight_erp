package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class HsePagesIntegrationTest extends PageTestSupport {

    static final String FACTS = "The officer on duty saw the worker without a vest at 09:42.";
    static final String NOTE = "Checked the photo with the officer of that shift.";

    Long worker;
    Long hse;
    Long manager;
    Long admin;
    Long otherAdmin;
    Long camera;
    Long zone;

    @BeforeEach
    void seed() {
        worker = userWithRole("HSE-W", "Wanda Worker", "WORKER");
        hse = userWithRole("HSE-H", "Hana Hse", "HSE_OFFICER");
        manager = userWithRole("HSE-M", "Mo Manager", "MANAGER");
        admin = userWithRole("HSE-A", "Ada Admin", "ADMIN");
        otherAdmin = userWithRole("HSE-A2", "Abe Admin", "ADMIN");
        zone = zone("HSE Zone");
        camera = camera("HSE-CAM", zone);
    }

    @Test
    void menuShowsHseWorkAndHidesManagerWorkAndTheTicketQueue() throws Exception {
        mvc.perform(get("/").with(as(hse)))
                .andExpect(content().string(not(containsString("href=\"/tickets/queue\""))))
                .andExpect(content().string(containsString("href=\"/violations/review\"")))
                .andExpect(content().string(containsString("href=\"/attendance\"")))
                .andExpect(content().string(not(containsString("href=\"/team\""))))
                .andExpect(content().string(not(containsString("href=\"/shifts\""))));
    }

    @Test
    void homeCountsWhatIsWaitingForEachRole() throws Exception {
        disputed();
        mvc.perform(get("/").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Violations to review")))
                .andExpect(content().string(not(containsString("Appeals to decide"))));
        mvc.perform(get("/").with(as(admin)))
                .andExpect(content().string(containsString("Appeals to decide")));
    }

    @Test
    void workersCannotReachTheReviewPagesOrActions() throws Exception {
        Long id = disputed();

        mvc.perform(get("/violations/review").with(as(worker))).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/queue").with(as(worker))).andExpect(status().isForbidden());
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "OVERTURNED").with(as(worker)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(disputeStatus(id)).isEqualTo("DISPUTED");
    }

    @Test
    void managersCannotDecideDisputes() throws Exception {
        Long id = disputed();

        mvc.perform(post("/violations/" + id + "/decision").param("decision", "UPHELD").with(as(manager)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/violations/review").with(as(manager))).andExpect(status().isForbidden());
    }

    /** SRS BR-61, BR-65: the Admin decides an appeal; HSE only gives the facts. */
    @Test
    void hseCannotDecideAnAppeal() throws Exception {
        Long id = disputed();
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE violation_id = ?", Long.class, id);

        mvc.perform(get("/violations/" + id).with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Decide this appeal"))));
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "OVERTURNED").with(as(hse)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/tickets/" + ticket).with(as(hse))).andExpect(status().isForbidden());
        assertThat(disputeStatus(id)).isEqualTo("DISPUTED");
    }

    /** SRS 3.2.16, 3.2.18: the queue holds what still needs a decision; the list holds everything. */
    @Test
    void theQueueListsOnlyOpenViolationsAndTheListShowsEveryOne() throws Exception {
        disputed();
        violation(null, camera, "NO_HARD_HAT_AND_VEST");

        mvc.perform(get("/violations/review").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hard hat and vest")))
                .andExpect(content().string(containsString("Pending review")))
                .andExpect(content().string(not(containsString("Wanda Worker"))));
        mvc.perform(get("/violations/all").with(as(hse)))
                .andExpect(content().string(containsString("Hard hat and vest")))
                .andExpect(content().string(containsString("Wanda Worker")));
        mvc.perform(get("/violations/all").param("status", "CONFIRMED").with(as(hse)))
                .andExpect(content().string(containsString("Wanda Worker")))
                .andExpect(content().string(not(containsString("Pending review</span>"))));
        mvc.perform(get("/violations/all").param("status", "NONSENSE").with(as(hse)))
                .andExpect(status().isNotFound());
    }

    @Test
    void decidingShowsTheReasonThenResolvesTheTicketWithTheNote() throws Exception {
        Long id = disputed();
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE violation_id = ?", Long.class, id);

        mvc.perform(get("/violations/" + id).with(as(admin)))
                .andExpect(content().string(containsString("I was wearing it")))
                .andExpect(content().string(containsString("Decide this appeal")));

        mvc.perform(post("/violations/" + id + "/decision")
                        .param("decision", "OVERTURNED").param("note", "Footage is clear").param("hseFacts", FACTS).with(as(admin)).with(csrf()))
                .andExpect(flash().attributeExists("success"));

        assertThat(disputeStatus(id)).isEqualTo("OVERTURNED");
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", ticket)).isEqualTo("RESOLVED");
        mvc.perform(get("/tickets/" + ticket).with(as(worker)))
                .andExpect(content().string(containsString("Footage is clear")));
        mvc.perform(get("/violations/" + id).with(as(admin)))
                .andExpect(content().string(not(containsString("Decide this appeal"))));
    }

    @Test
    void aSecondDecisionIsRefused() throws Exception {
        Long id = disputed();
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "UPHELD").param("note", NOTE).param("hseFacts", FACTS).with(as(admin)).with(csrf()));

        mvc.perform(post("/violations/" + id + "/decision").param("decision", "OVERTURNED").param("note", NOTE).param("hseFacts", FACTS).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "This ticket is already resolved"));

        assertThat(disputeStatus(id)).isEqualTo("UPHELD");
    }

    @Test
    void aDecisionMustBeUpheldOrOverturned() throws Exception {
        Long id = disputed();

        mvc.perform(post("/violations/" + id + "/decision").param("note", NOTE).param("hseFacts", FACTS).with(as(admin)).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "NONE").param("note", NOTE).param("hseFacts", FACTS).with(as(admin)).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        // A value the form can't send is a bad request, not a missing page
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "BOGUS").param("note", NOTE).param("hseFacts", FACTS).with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(disputeStatus(id)).isEqualTo("DISPUTED");
    }

    /** SRS UC-24 EX-01: an Admin cannot handle their own ticket; another Admin does. */
    @Test
    void anAdminCannotDecideTheirOwnAppealButAnotherAdminCan() throws Exception {
        // An admin who works on site (Site group) can be confirmed and appeal
        jdbc.update("UPDATE app_user SET staff_group = 'SITE' WHERE id = ?", admin);
        Long own = violation(admin, camera, "NO_VEST");
        mvc.perform(post("/violations/" + own + "/dispute").param("reason", "Mine, but I wore it").with(as(admin)).with(csrf()));

        mvc.perform(get("/violations/" + own).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Decide this appeal"))));
        mvc.perform(post("/violations/" + own + "/decision").param("decision", "OVERTURNED").param("note", NOTE).param("hseFacts", FACTS).with(as(admin)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(disputeStatus(own)).isEqualTo("DISPUTED");

        mvc.perform(post("/violations/" + own + "/decision").param("decision", "OVERTURNED").param("note", NOTE).param("hseFacts", FACTS).with(as(otherAdmin)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(disputeStatus(own)).isEqualTo("OVERTURNED");
    }

    @Test
    void anAdminCannotHandleTheirOwnTicket() throws Exception {
        Long id = ticket(category("Other"), admin, "My own request");

        mvc.perform(get("/tickets/" + id).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Handle this ticket"))));
        mvc.perform(post("/tickets/" + id + "/resolve").with(as(admin)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/" + id).with(as(otherAdmin)))
                .andExpect(content().string(containsString("Handle this ticket")))
                .andExpect(content().string(containsString("Abe Admin")))
                .andExpect(content().string(not(containsString("Ada Admin</option>"))));
    }

    /** BR-48, MSG35, MSG37: HSE confirms the person; then the worker sees it and may appeal. */
    @Test
    void hseConfirmsWhoItWasAndThenTheWorkerCanAppeal() throws Exception {
        Long id = violation(null, camera, "NO_VEST");

        mvc.perform(get("/violations/" + id).with(as(hse)))
                .andExpect(content().string(containsString("Confirm who it was")))
                .andExpect(content().string(containsString("Wanda Worker (HSE-W)")));
        mvc.perform(get("/violations/" + id).with(as(worker))).andExpect(status().isForbidden());
        mvc.perform(post("/violations/" + id + "/confirm").param("version", "0").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", "Choose the person, or mark the violation as Stranger."));
        mvc.perform(post("/violations/" + id + "/confirm").param("personId", worker.toString()).param("version", "0").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("success", "The violation was confirmed."));

        mvc.perform(get("/violations/" + id).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Send appeal")));
        mvc.perform(post("/violations/" + id + "/confirm").param("personId", hse.toString()).param("version", "1").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", "This violation was already decided by another officer. Reload the page to see the decision."));
    }

    /** SRS BR-61: every ticket goes to the Admin queue, whatever its category. */
    @Test
    void everyTicketGoesToTheAdminQueue() throws Exception {
        joinTeam(worker, team("Queue team", manager));
        ticket(category("Safety concern"), worker, "Loose guard rail");
        ticket(category("Equipment request"), worker, "New boots");

        mvc.perform(get("/tickets/queue").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Loose guard rail")))
                .andExpect(content().string(containsString("New boots")));
        mvc.perform(get("/tickets/queue").with(as(hse))).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/queue").with(as(manager))).andExpect(status().isForbidden());
    }

    @Test
    void assigningAndResolvingATicketMovesItThroughTheQueue() throws Exception {
        Long id = ticket(category("Safety concern"), worker, "Loose guard rail");

        mvc.perform(get("/tickets/" + id).with(as(admin)))
                .andExpect(content().string(containsString("Handle this ticket")))
                .andExpect(content().string(containsString("Ada Admin")))
                .andExpect(content().string(not(containsString("Mo Manager"))));
        mvc.perform(post("/tickets/" + id + "/assignee").param("assigneeId", admin.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", id)).isEqualTo("IN_PROGRESS");

        mvc.perform(post("/tickets/" + id + "/resolve").param("note", "Rail tightened").with(as(admin)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", id)).isEqualTo("RESOLVED");

        mvc.perform(get("/tickets/queue").with(as(admin)))
                .andExpect(content().string(not(containsString("Loose guard rail"))));
        mvc.perform(get("/tickets/queue").param("resolved", "true").with(as(admin)))
                .andExpect(content().string(containsString("Loose guard rail")));
        mvc.perform(get("/tickets/" + id).with(as(worker)))
                .andExpect(content().string(containsString("Rail tightened")))
                .andExpect(content().string(not(containsString("Handle this ticket"))));
    }

    @Test
    void aTicketCannotBeAssignedToSomeoneWhoIsNotAnAdmin() throws Exception {
        Long id = ticket(category("Safety concern"), worker, "Loose guard rail");

        mvc.perform(post("/tickets/" + id + "/assignee").param("assigneeId", hse.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "That user cannot handle this ticket"));
        mvc.perform(post("/tickets/" + id + "/assignee").param("assigneeId", manager.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "That user cannot handle this ticket"));
        mvc.perform(post("/tickets/" + id + "/assignee").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "That user cannot handle this ticket"));
    }

    @Test
    void hseCannotReadOrHandleSomeoneElsesTicket() throws Exception {
        Long id = ticket(category("Safety concern"), worker, "Loose guard rail");

        mvc.perform(get("/tickets/" + id).with(as(hse))).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + id + "/resolve").with(as(hse)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + id + "/comments").param("body", "Hi").with(as(hse)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** SRS BR-61: the team leader of the creator reads and comments, but never decides. */
    @Test
    void theTeamLeaderReadsAndCommentsOnTeamTicketsButCannotDecide() throws Exception {
        joinTeam(worker, team("Leader team", manager));
        Long id = ticket(category("Leave request"), worker, "Day off Friday");

        mvc.perform(get("/tickets").with(as(manager)))
                .andExpect(content().string(containsString("My team&#39;s tickets")))
                .andExpect(content().string(containsString("Day off Friday")));
        mvc.perform(get("/tickets/" + id).with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Post comment")))
                .andExpect(content().string(not(containsString("Handle this ticket"))));
        mvc.perform(post("/tickets/" + id + "/comments").param("body", "He swapped with Tam").with(as(manager)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post("/tickets/" + id + "/resolve").with(as(manager)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/" + id).with(as(worker)))
                .andExpect(content().string(containsString("He swapped with Tam")));
    }

    @Test
    void aLeaderCannotReadTicketsFromAnotherTeam() throws Exception {
        Long otherLeader = userWithRole("HSE-M2", "Ola Leader", "MANAGER");
        joinTeam(worker, team("Leader team", manager));
        team("Other team", otherLeader);
        Long id = ticket(category("Leave request"), worker, "Day off Friday");

        mvc.perform(get("/tickets/" + id).with(as(otherLeader))).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + id + "/comments").param("body", "x").with(as(otherLeader)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDisputeTicketPointsToTheDecisionInsteadOfOfferingResolve() throws Exception {
        Long id = disputed();
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE violation_id = ?", Long.class, id);

        mvc.perform(get("/tickets/" + ticket).with(as(admin)))
                .andExpect(content().string(containsString("Go to the decision")))
                .andExpect(content().string(not(containsString("Resolve ticket"))));
        mvc.perform(post("/tickets/" + ticket + "/resolve").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "Resolve a PPE dispute by deciding its violation"));
    }

    @Test
    void anOfficerCanAddAndCorrectACheckIn() throws Exception {
        mvc.perform(get("/attendance").param("onSite", "true").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nobody is checked in")));
        mvc.perform(post("/attendance")
                        .param("workerId", worker.toString()).param("zoneId", zone.toString())
                        .param("checkInAt", "2026-11-04T08:00").param("note", "Scanner was down")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Long id = jdbc.queryForObject("SELECT id FROM check_in WHERE user_id = ?", Long.class, worker);
        assertThat(textColumn("SELECT check_in_at AT TIME ZONE 'Asia/Ho_Chi_Minh' FROM check_in WHERE id = ?", id))
                .startsWith("2026-11-04 08:00");
        mvc.perform(get("/attendance").param("day", "2026-11-04").with(as(hse)))
                .andExpect(content().string(containsString("Scanner was down")))
                .andExpect(content().string(containsString("Wanda Worker (HSE-W)")));

        mvc.perform(get("/attendance/" + id + "/edit").with(as(hse)))
                .andExpect(content().string(containsString("Correct: Wanda Worker")));
        mvc.perform(post("/attendance/" + id).param("checkInAt", "2026-11-04T08:00").param("checkOutAt", "2026-11-04T07:00").param("note", "Left by the side gate").param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Check-out must be after check-in")));
        mvc.perform(post("/attendance/" + id).param("checkInAt", "2026-11-04T08:00").param("checkOutAt", "2026-11-04T16:00").param("note", "Left by the side gate").param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT check_out_at IS NOT NULL FROM check_in WHERE id = ?", Boolean.class, id)).isTrue();

        // The same form again is now out of date
        mvc.perform(post("/attendance/" + id).param("checkInAt", "2026-11-04T09:00").param("note", "Came in by the side gate").param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void theEditPageShowsWhoCorrectedAVisitAndWhatChanged() throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO check_in (user_id, check_in_at, check_out_at, method, recorded_by, note)
                VALUES (?, '2026-11-04 01:00+00', '2026-11-04 02:00+00', 'MANUAL', ?, 'Typed wrong') RETURNING id""",
                Long.class, worker, hse);
        String corrector = jdbc.queryForObject("SELECT full_name FROM app_user WHERE id = ?", String.class, hse);

        mvc.perform(get("/attendance/" + id + "/edit").with(as(hse)))
                .andExpect(content().string(containsString("This visit hasn&#39;t been corrected.")));

        mvc.perform(post("/attendance/" + id).param("checkInAt", "2026-11-04T08:00").param("checkOutAt", "2026-11-04T16:00")
                        .param("note", "<b>Left late</b>").param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        flush();

        mvc.perform(get("/attendance/" + id + "/edit").with(as(hse)))
                .andExpect(content().string(containsString("Change history")))
                .andExpect(content().string(containsString(corrector)))
                .andExpect(content().string(containsString("Typed wrong → &lt;b&gt;Left late&lt;/b&gt;")))
                .andExpect(content().string(containsString("Checked out: ")))
                .andExpect(content().string(not(containsString("Checked in: "))));
    }

    @Test
    void aManualCheckInNeedsAWorkerAndATime() throws Exception {
        mvc.perform(post("/attendance").param("checkInAt", "2026-11-04T08:00").with(as(hse)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Choose a worker")));
        mvc.perform(post("/attendance").param("workerId", worker.toString()).with(as(hse)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("A check-in time is required")));
        mvc.perform(post("/attendance").param("workerId", worker.toString()).param("checkInAt", "not a date").with(as(hse)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private Long disputed() throws Exception {
        Long id = violation(worker, camera, "NO_VEST");
        mvc.perform(post("/violations/" + id + "/dispute").param("reason", "I was wearing it").with(as(worker)).with(csrf()));
        return id;
    }

    private String disputeStatus(Long violation) {
        return textColumn("SELECT dispute_status FROM violation WHERE id = ?", violation);
    }

    @Test
    void longListsArePagedAndKeepTheirFilter() throws Exception {
        for (int i = 1; i <= 21; i++) {
            violation(null, camera, "NO_VEST");
        }

        mvc.perform(get("/violations/review?zoneId=" + zone).with(as(hse)))
                .andExpect(content().string(containsString("Page 1 of 2")))
                .andExpect(content().string(containsString("/violations/review?zoneId=" + zone + "&amp;page=1")))
                .andExpect(content().string(not(containsString(">Previous<"))));
        mvc.perform(get("/violations/review").param("zoneId", zone.toString()).param("page", "1").with(as(hse)))
                .andExpect(content().string(containsString("Page 2 of 2")))
                .andExpect(content().string(containsString(">Previous<")))
                .andExpect(content().string(not(containsString(">Next<"))))
                .andExpect(content().string(containsString("Vest")));
    }

    @Test
    void oddPageNumbersDoNotBreakTheList() throws Exception {
        violation(null, camera, "NO_HARD_HAT");

        mvc.perform(get("/violations/all").param("page", "-5").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hard hat")));
        mvc.perform(get("/violations/all").param("page", "99").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("page=0")));
        mvc.perform(get("/violations/all").param("page", "abc").with(as(hse)))
                .andExpect(status().isNotFound());
    }
}
