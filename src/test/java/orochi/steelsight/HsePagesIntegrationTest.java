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

    Long worker;
    Long hse;
    Long manager;
    Long camera;
    Long zone;

    @BeforeEach
    void seed() {
        worker = userWithRole("HSE-W", "Wanda Worker", "WORKER");
        hse = userWithRole("HSE-H", "Hana Hse", "HSE_OFFICER");
        manager = userWithRole("HSE-M", "Mo Manager", "MANAGER");
        zone = zone("HSE Zone");
        camera = camera("HSE-CAM", zone);
    }

    @Test
    void menuShowsHseWorkAndHidesManagerWork() throws Exception {
        mvc.perform(get("/").with(as(hse)))
                .andExpect(content().string(containsString("href=\"/tickets/queue\"")))
                .andExpect(content().string(containsString("href=\"/violations/review\"")))
                .andExpect(content().string(containsString("href=\"/attendance\"")))
                .andExpect(content().string(not(containsString("href=\"/team\""))))
                .andExpect(content().string(not(containsString("href=\"/shifts\""))));
    }

    @Test
    void homeCountsWhatIsWaiting() throws Exception {
        disputed();
        mvc.perform(get("/").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disputes to decide")));
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

    @Test
    void reviewListsDisputesAndViolationsWithNoWorker() throws Exception {
        disputed();
        violation(null, camera, "NO_GLOVES");

        mvc.perform(get("/violations/review").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wanda Worker")))
                .andExpect(content().string(not(containsString("No gloves"))));
        mvc.perform(get("/violations/review").param("view", "UNIDENTIFIED").with(as(hse)))
                .andExpect(content().string(containsString("No gloves")))
                .andExpect(content().string(containsString("Worker unknown")));
        mvc.perform(get("/violations/review").param("view", "ALL").with(as(hse)))
                .andExpect(content().string(containsString("No gloves")))
                .andExpect(content().string(containsString("Wanda Worker")));
        mvc.perform(get("/violations/review").param("view", "NONSENSE").with(as(hse)))
                .andExpect(status().isNotFound());
    }

    @Test
    void decidingShowsTheReasonThenResolvesTheTicketWithTheNote() throws Exception {
        Long id = disputed();
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE violation_id = ?", Long.class, id);

        mvc.perform(get("/violations/" + id).with(as(hse)))
                .andExpect(content().string(containsString("I was wearing it")))
                .andExpect(content().string(containsString("Decide this dispute")));

        mvc.perform(post("/violations/" + id + "/decision")
                        .param("decision", "OVERTURNED").param("note", "Footage is clear").with(as(hse)).with(csrf()))
                .andExpect(flash().attributeExists("success"));

        assertThat(disputeStatus(id)).isEqualTo("OVERTURNED");
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", ticket)).isEqualTo("RESOLVED");
        mvc.perform(get("/tickets/" + ticket).with(as(worker)))
                .andExpect(content().string(containsString("Footage is clear")));
        mvc.perform(get("/violations/" + id).with(as(hse)))
                .andExpect(content().string(not(containsString("Decide this dispute"))));
    }

    @Test
    void aSecondDecisionIsRefused() throws Exception {
        Long id = disputed();
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "UPHELD").with(as(hse)).with(csrf()));

        mvc.perform(post("/violations/" + id + "/decision").param("decision", "OVERTURNED").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", "Only a disputed violation can be decided"));

        assertThat(disputeStatus(id)).isEqualTo("UPHELD");
    }

    @Test
    void aDecisionMustBeUpheldOrOverturned() throws Exception {
        Long id = disputed();

        mvc.perform(post("/violations/" + id + "/decision").with(as(hse)).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "NONE").with(as(hse)).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/violations/" + id + "/decision").param("decision", "BOGUS").with(as(hse)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(disputeStatus(id)).isEqualTo("DISPUTED");
    }

    @Test
    void anOfficerCanSeeButNotDecideTheirOwnViolation() throws Exception {
        Long own = violation(hse, camera, "NO_VEST");
        mvc.perform(post("/violations/" + own + "/dispute").param("reason", "Mine").with(as(hse)).with(csrf()));

        mvc.perform(get("/violations/" + own).with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Decide this dispute"))));
        mvc.perform(post("/violations/" + own + "/decision").param("decision", "OVERTURNED").with(as(hse)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(disputeStatus(own)).isEqualTo("DISPUTED");
    }

    @Test
    void anUnidentifiedViolationCanBeGivenAWorkerWhoCanThenDisputeIt() throws Exception {
        Long id = violation(null, camera, "NO_GLOVES");

        mvc.perform(get("/violations/" + id).with(as(hse)))
                .andExpect(content().string(containsString("Who is this?")))
                .andExpect(content().string(containsString("Wanda Worker (HSE-W)")));
        mvc.perform(post("/violations/" + id + "/worker").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", "Choose a worker"));
        mvc.perform(post("/violations/" + id + "/worker").param("workerId", worker.toString()).with(as(hse)).with(csrf()))
                .andExpect(flash().attributeExists("success"));

        mvc.perform(get("/violations/" + id).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Send dispute")));
        mvc.perform(post("/violations/" + id + "/worker").param("workerId", hse.toString()).with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", "This violation already has a worker"));
    }

    @Test
    void queueShowsOnlyTheCategoriesYourRoleHandles() throws Exception {
        joinTeam(worker, team("Queue team", manager));
        ticket(category("Safety concern"), worker, "Loose guard rail");
        ticket(category("Equipment request"), worker, "New boots");

        mvc.perform(get("/tickets/queue").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Loose guard rail")))
                .andExpect(content().string(not(containsString("New boots"))));
        mvc.perform(get("/tickets/queue").with(as(manager)))
                .andExpect(content().string(containsString("New boots")))
                .andExpect(content().string(not(containsString("Loose guard rail"))));
    }

    @Test
    void assigningAndResolvingATicketMovesItThroughTheQueue() throws Exception {
        Long id = ticket(category("Safety concern"), worker, "Loose guard rail");

        mvc.perform(get("/tickets/" + id).with(as(hse)))
                .andExpect(content().string(containsString("Handle this ticket")))
                .andExpect(content().string(containsString("Hana Hse")))
                .andExpect(content().string(not(containsString("Mo Manager"))));
        mvc.perform(post("/tickets/" + id + "/assignee").param("assigneeId", hse.toString()).with(as(hse)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", id)).isEqualTo("IN_PROGRESS");

        mvc.perform(post("/tickets/" + id + "/resolve").param("note", "Rail tightened").with(as(hse)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", id)).isEqualTo("RESOLVED");

        mvc.perform(get("/tickets/queue").with(as(hse)))
                .andExpect(content().string(not(containsString("Loose guard rail"))));
        mvc.perform(get("/tickets/queue").param("resolved", "true").with(as(hse)))
                .andExpect(content().string(containsString("Loose guard rail")));
        mvc.perform(get("/tickets/" + id).with(as(worker)))
                .andExpect(content().string(containsString("Rail tightened")))
                .andExpect(content().string(not(containsString("Handle this ticket"))));
    }

    @Test
    void aTicketCannotBeAssignedToSomeoneOutsideTheHandlingRole() throws Exception {
        Long id = ticket(category("Safety concern"), worker, "Loose guard rail");

        mvc.perform(post("/tickets/" + id + "/assignee").param("assigneeId", manager.toString()).with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", "That user cannot handle this ticket"));
        mvc.perform(post("/tickets/" + id + "/assignee").with(as(hse)).with(csrf()))
                .andExpect(flash().attribute("error", "That user cannot handle this ticket"));
    }

    @Test
    void anotherRolesTicketCannotBeHandled() throws Exception {
        Long id = ticket(category("Equipment request"), worker, "New boots");

        mvc.perform(get("/tickets/" + id).with(as(hse))).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + id + "/resolve").with(as(hse)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void aDisputeTicketPointsToTheDecisionInsteadOfOfferingResolve() throws Exception {
        Long id = disputed();
        Long ticket = jdbc.queryForObject("SELECT id FROM ticket WHERE violation_id = ?", Long.class, id);

        mvc.perform(get("/tickets/" + ticket).with(as(hse)))
                .andExpect(content().string(containsString("Go to the decision")))
                .andExpect(content().string(not(containsString("Resolve ticket"))));
        mvc.perform(post("/tickets/" + ticket + "/resolve").with(as(hse)).with(csrf()))
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
        mvc.perform(get("/attendance").with(as(hse)))
                .andExpect(content().string(containsString("Scanner was down")))
                .andExpect(content().string(containsString("Wanda Worker (HSE-W)")));

        mvc.perform(get("/attendance/" + id + "/edit").with(as(hse)))
                .andExpect(content().string(containsString("Correct: Wanda Worker")));
        mvc.perform(post("/attendance/" + id).param("checkInAt", "2026-11-04T08:00").param("checkOutAt", "2026-11-04T07:00").param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Check-out must be after check-in")));
        mvc.perform(post("/attendance/" + id).param("checkInAt", "2026-11-04T08:00").param("checkOutAt", "2026-11-04T16:00").param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT check_out_at IS NOT NULL FROM check_in WHERE id = ?", Boolean.class, id)).isTrue();

        // The same form again is now out of date
        mvc.perform(post("/attendance/" + id).param("checkInAt", "2026-11-04T09:00").param("version", "0")
                        .with(as(hse)).with(csrf()))
                .andExpect(status().isConflict());
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
            violation(null, camera, "NO_GLOVES");
        }

        mvc.perform(get("/violations/review?view=UNIDENTIFIED").with(as(hse)))
                .andExpect(content().string(containsString("Page 1 of 2")))
                .andExpect(content().string(containsString("/violations/review?view=UNIDENTIFIED&amp;page=1")))
                .andExpect(content().string(not(containsString(">Previous<"))));
        mvc.perform(get("/violations/review").param("view", "UNIDENTIFIED").param("page", "1").with(as(hse)))
                .andExpect(content().string(containsString("Page 2 of 2")))
                .andExpect(content().string(containsString(">Previous<")))
                .andExpect(content().string(not(containsString(">Next<"))))
                .andExpect(content().string(containsString("No gloves")));
    }

    @Test
    void oddPageNumbersDoNotBreakTheList() throws Exception {
        violation(null, camera, "NO_GLOVES");

        mvc.perform(get("/violations/review").param("view", "ALL").param("page", "-5").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No gloves")));
        mvc.perform(get("/violations/review").param("view", "ALL").param("page", "99").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("No violations have been recorded"))))
                .andExpect(content().string(containsString("page=0")));
        mvc.perform(get("/violations/review").param("view", "ALL").param("page", "abc").with(as(hse)))
                .andExpect(status().isNotFound());
    }
}
