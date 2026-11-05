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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Managers can look at every worker, but only change things for the teams they run. */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class WorkerAccessPagesTest extends PageTestSupport {

    Long manager;
    Long otherManager;
    Long hse;
    Long admin;
    Long mine;
    Long theirs;
    Long zone;
    Long camera;

    @BeforeEach
    void seed() {
        manager = userWithRole("WA-M1", "Mina Manager", "MANAGER");
        otherManager = userWithRole("WA-M2", "Omar Other", "MANAGER");
        hse = userWithRole("WA-H", "Hana Hse", "HSE_OFFICER");
        admin = userWithRole("WA-A", "Ada Admin", "ADMIN");
        mine = userWithRole("WA-W1", "Wanda Mine", "WORKER");
        theirs = userWithRole("WA-W2", "Tom Theirs", "WORKER");
        joinTeam(mine, team("WA Team 1", manager));
        joinTeam(theirs, team("WA Team 2", otherManager));
        zone = zone("WA Zone");
        camera = camera("WA-CAM", zone);
    }

    @Test
    void managerAndHseSeeEveryWorkerWithTheirScoreLowestFirst() throws Exception {
        violation(theirs, camera, "NO_VEST");
        for (Long viewer : new Long[]{manager, hse}) {
            String page = mvc.perform(get("/workers").param("q", "WA-W").with(as(viewer)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Wanda Mine")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(page.indexOf("Tom Theirs")).isLessThan(page.indexOf("Wanda Mine")).isPositive();
            assertThat(page).contains("90");
        }
    }

    @Test
    void searchTreatsWildcardsAsPlainText() throws Exception {
        mvc.perform(get("/workers").param("q", "%").with(as(manager)))
                .andExpect(content().string(not(containsString("Wanda Mine"))));
        mvc.perform(get("/workers").param("q", "wanda").with(as(manager)))
                .andExpect(content().string(containsString("Wanda Mine")));
    }

    @Test
    void workerCannotSeeTheWorkerPages() throws Exception {
        mvc.perform(get("/workers").with(as(mine))).andExpect(status().isForbidden());
        mvc.perform(get("/workers/" + theirs).with(as(mine))).andExpect(status().isForbidden());
        mvc.perform(get("/safety/shifts").with(as(mine))).andExpect(status().isForbidden());
    }

    @Test
    void managerSeesAnotherTeamsWorkerButCannotChangeThem() throws Exception {
        violation(theirs, camera, "NO_VEST");

        mvc.perform(get("/workers/" + theirs).with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tom Theirs")))
                .andExpect(content().string(containsString("No vest")))
                .andExpect(content().string(not(containsString("/team/" + theirs + "/edit"))))
                .andExpect(content().string(not(containsString("/shifts/new?workerId=" + theirs))));

        mvc.perform(get("/team/" + theirs + "/edit").with(as(manager))).andExpect(status().isForbidden());
    }

    @Test
    void managerGetsChangeLinksForTheirOwnWorker() throws Exception {
        mvc.perform(get("/workers/" + mine).with(as(manager)))
                .andExpect(content().string(containsString("/team/" + mine + "/edit")))
                .andExpect(content().string(containsString("/shifts/new?workerId=" + mine)));
    }

    @Test
    void managerStillCannotOpenViolationPages() throws Exception {
        Long violation = violation(theirs, camera, "NO_VEST");
        mvc.perform(get("/violations/" + violation).with(as(manager))).andExpect(status().isForbidden());
    }

    @Test
    void onlyWorkersHaveAWorkerPage() throws Exception {
        mvc.perform(get("/workers/" + otherManager).with(as(manager))).andExpect(status().isNotFound());
    }

    @Test
    void managerHandlesTicketsFromTheirOwnTeamOnly() throws Exception {
        Long category = category("Equipment request");
        Long fromMine = ticket(category, mine, "Boots from mine");
        Long fromTheirs = ticket(category, theirs, "Boots from theirs");

        mvc.perform(get("/tickets/queue").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Boots from mine")))
                .andExpect(content().string(not(containsString("Boots from theirs"))));
        mvc.perform(get("/tickets/" + fromMine).with(as(manager))).andExpect(status().isOk());
        mvc.perform(get("/tickets/" + fromTheirs).with(as(manager))).andExpect(status().isForbidden());

        mvc.perform(post("/tickets/" + fromTheirs + "/resolve").with(as(manager)).with(csrf())).andExpect(status().isForbidden());
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", fromTheirs)).isEqualTo("OPEN");

        mvc.perform(post("/tickets/" + fromMine + "/resolve").with(as(manager)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", fromMine)).isEqualTo("RESOLVED");
    }

    @Test
    void aTicketFromSomeoneWithNoTeamReachesOnlyAnAdmin() throws Exception {
        Long loner = userWithRole("WA-W3", "Lone Worker", "WORKER");
        Long ticket = ticket(category("Equipment request"), loner, "Boots from nobody");

        mvc.perform(get("/tickets/" + ticket).with(as(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/" + ticket).with(as(otherManager))).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/" + ticket).with(as(admin))).andExpect(status().isOk());
    }

    @Test
    void hseTicketsDoNotDependOnTheTeam() throws Exception {
        Long ticket = ticket(category("Safety concern"), theirs, "Loose rail");
        mvc.perform(get("/tickets/queue").with(as(hse))).andExpect(content().string(containsString("Loose rail")));
        mvc.perform(post("/tickets/" + ticket + "/resolve").with(as(hse)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", ticket)).isEqualTo("RESOLVED");
    }

    @Test
    void aWorkerAtWarningLevelSeesTheReminderAndTheBell() throws Exception {
        safetyHits(mine, 3);

        mvc.perform(get("/").with(as(mine)))
                .andExpect(content().string(containsString("Safety warning")))
                .andExpect(content().string(containsString("Hard hat")))
                .andExpect(content().string(containsString("PPE vest")))
                .andExpect(content().string(containsString("bell-count")))
                .andExpect(content().string(not(containsString("paycheck"))));
    }

    @Test
    void aWorkerAtDangerLevelIsToldAboutTheirPay() throws Exception {
        safetyHits(mine, 6);

        mvc.perform(get("/notifications").with(as(mine)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your paycheck may be deducted under company policy")))
                .andExpect(content().string(containsString("Hard hat")));
    }

    @Test
    void notificationsAreYourOwnAndCanBeMarkedRead() throws Exception {
        safetyHits(mine, 3);
        Long notification = jdbc.queryForObject("SELECT id FROM notification WHERE user_id = ?", Long.class, mine);

        mvc.perform(post("/notifications/" + notification + "/read").with(as(theirs)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/notifications").with(as(theirs))).andExpect(content().string(containsString("You have no notifications")));

        mvc.perform(post("/notifications/" + notification + "/read").with(as(mine)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT read_at IS NOT NULL FROM notification WHERE id = ?", Boolean.class, notification)).isTrue();
    }

    @Test
    void hseSeesHowUnsafeEachTimeSlotWas() throws Exception {
        shift(mine, zone, "timestamptz '2026-03-02 08:00+07'", "timestamptz '2026-03-02 16:00+07'");
        jdbc.update("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, 'NO_VEST', timestamptz '2026-03-02 09:00+07', 'x.jpg')""", mine, camera);

        mvc.perform(get("/safety/shifts").param("week", "2026-03-02").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("12.5")));
        mvc.perform(get("/safety/shifts").with(as(manager))).andExpect(status().isForbidden());
    }

    @Test
    void theTimetableMovesByWeekAndABadWeekIsNotFound() throws Exception {
        shift(mine, zone, "timestamptz '2026-03-04 08:00+07'", "timestamptz '2026-03-04 16:00+07'");

        mvc.perform(get("/timetable").param("week", "2026-03-04").with(as(mine)))
                .andExpect(content().string(containsString("(8 h)")))
                .andExpect(content().string(containsString("week=2026-03-09")))
                .andExpect(content().string(not(containsString("/shifts/"))));
        mvc.perform(get("/timetable").param("week", "2026-03-09").with(as(mine)))
                .andExpect(content().string(not(containsString("(8 h)"))));
        mvc.perform(get("/timetable").param("week", "soon").with(as(mine))).andExpect(status().isNotFound());
    }

    /** Violations a block apart, so each counts, from the start of the current month in site time. */
    private void safetyHits(Long user, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                    VALUES (?, ?, 'NO_VEST',
                            date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh'
                                + interval '1 minute' + make_interval(mins => ?), 'x.jpg')""", user, camera, i * 30);
        }
    }
}
