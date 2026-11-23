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
            assertThat(page).contains("95");   // 100 - 5 (BR-57)
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
                .andExpect(content().string(containsString("Vest")))
                .andExpect(content().string(not(containsString("/team/" + theirs + "/edit"))))
                .andExpect(content().string(not(containsString("/shifts/new?workerId=" + theirs))));

        mvc.perform(get("/team/" + theirs + "/edit").with(as(manager))).andExpect(status().isForbidden());
    }

    @Test
    void managerCanPlanTheirOwnWorkersShiftsButNotEditThem() throws Exception {
        mvc.perform(get("/workers/" + mine).with(as(manager)))
                .andExpect(content().string(not(containsString("/team/" + mine + "/edit"))))
                .andExpect(content().string(containsString("/shifts/new?workerId=" + mine)));
        mvc.perform(get("/team/" + mine + "/edit").with(as(manager))).andExpect(status().isForbidden());
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
    void ticketsGoToAdminWhicheverTeamTheyComeFrom() throws Exception {
        Long fromMine = ticket(category("Other"), mine, "Boots from mine");
        Long loner = userWithRole("WA-W3", "Lone Worker", "WORKER");
        Long fromNobody = ticket(category("Other"), loner, "Boots from nobody");

        mvc.perform(get("/tickets/queue").with(as(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/" + fromMine).with(as(manager))).andExpect(status().isOk());       // their own team
        mvc.perform(get("/tickets/" + fromNobody).with(as(manager))).andExpect(status().isForbidden());
        for (Long ticket : new Long[]{fromMine, fromNobody}) {
            mvc.perform(get("/tickets/" + ticket).with(as(admin))).andExpect(status().isOk());
        }
        mvc.perform(post("/tickets/" + fromMine + "/resolve").with(as(manager)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/tickets/queue").with(as(admin)))
                .andExpect(content().string(containsString("Boots from mine")))
                .andExpect(content().string(containsString("Boots from nobody")));
    }

    @Test
    void safetyConcernsGoToTheAdminNotHse() throws Exception {
        Long ticket = ticket(category("Other"), theirs, "Loose rail");
        mvc.perform(get("/tickets/queue").with(as(hse))).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + ticket + "/resolve").with(as(hse)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + ticket + "/resolve").with(as(admin)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", ticket)).isEqualTo("RESOLVED");
    }

    /** BR-58, BR-59: 4 confirmed violations make 80 points, bonus level 1; the worker is told and reminded. */
    @Test
    void aWorkerAtLevelOneSeesTheReminderAndTheBell() throws Exception {
        safetyHits(mine, 4);

        mvc.perform(get("/").with(as(mine)))
                .andExpect(content().string(containsString("Level 1: bonus -2%")))
                .andExpect(content().string(containsString("Hard hat")))
                .andExpect(content().string(containsString("PPE vest")))
                .andExpect(content().string(containsString("bell-count")));
    }

    /** BR-59: when the level becomes worse, the notice says so clearly. */
    @Test
    void aWorkerWhoseLevelGetsWorseIsToldTheReduction() throws Exception {
        safetyHits(mine, 8);

        mvc.perform(get("/notifications").with(as(mine)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("the bonus is reduced by 6%")))
                .andExpect(content().string(containsString("Violation confirmed")));
    }

    @Test
    void notificationsAreYourOwnAndCanBeMarkedRead() throws Exception {
        safetyHits(mine, 1);
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
    /** Violations of this month that HSE confirms for the person on the review page, one by one. */
    private void safetyHits(Long user, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                    VALUES (?, 'NO_VEST',
                            date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh'
                                + interval '1 minute' + make_interval(mins => ?), 'x.jpg') RETURNING id""", Long.class, camera, i * 30);
            mvc.perform(post("/violations/" + id + "/confirm").param("personId", user.toString()).param("version", "0")
                            .with(as(hse)).with(csrf()))
                    .andExpect(flash().attribute("success", "The violation was confirmed."));
        }
        jdbc.update("DELETE FROM notification WHERE user_id <> ?", user);
    }
}
