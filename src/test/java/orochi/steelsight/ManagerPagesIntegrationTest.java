package orochi.steelsight;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class ManagerPagesIntegrationTest extends PageTestSupport {

    @org.springframework.beans.factory.annotation.Autowired orochi.steelsight.jobs.SiteJobs siteJobs;
    @org.springframework.beans.factory.annotation.Autowired jakarta.persistence.EntityManager entityManager;

    Long manager;
    Long otherManager;
    Long worker;
    Long outsider;
    Long admin;
    Long hse;
    Long zone;

    @BeforeEach
    void seed() {
        manager = userWithRole("MGR-M1", "Mo Manager", "MANAGER");
        otherManager = userWithRole("MGR-M2", "Max Other", "MANAGER");
        worker = userWithRole("MGR-W1", "Wanda Worker", "WORKER");
        outsider = userWithRole("MGR-W2", "Otto Outsider", "WORKER");
        admin = userWithRole("MGR-A", "Ada Admin", "ADMIN");
        hse = userWithRole("MGR-H", "Hana Hse", "HSE_OFFICER");
        joinTeam(worker, team("Team A", manager));
        joinTeam(outsider, team("Team B", otherManager));
        zone = zone("Mgr Zone");
    }

    @Test
    void menuShowsManagerWorkAndHidesHseWork() throws Exception {
        mvc.perform(get("/").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/shifts\"")))
                .andExpect(content().string(containsString("href=\"/team\"")))
                .andExpect(content().string(not(containsString("href=\"/tickets/queue\""))))
                .andExpect(content().string(not(containsString("href=\"/violations/review\""))));
    }

    @Test
    void adminCanOpenEveryManagementPage() throws Exception {
        for (String page : new String[]{"/", "/shifts", "/shifts/new", "/team", "/tickets/queue", "/violations/review", "/attendance", "/attendance/new"}) {
            mvc.perform(get(page).with(as(admin))).andExpect(status().isOk());
        }
        mvc.perform(get("/team").with(as(admin)))
                .andExpect(content().string(containsString("Wanda Worker")))
                .andExpect(content().string(containsString("Otto Outsider")));
    }

    @Test
    void workersAndHseCannotManageShiftsOrTeams() throws Exception {
        for (Long person : new Long[]{worker, hse}) {
            for (String page : new String[]{"/shifts", "/shifts/new", "/team"}) {
                mvc.perform(get(page).with(as(person))).andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/attendance").with(as(worker))).andExpect(status().isForbidden());
    }

    @Test
    void gridShowsOnlyYourTeamAndNavigates() throws Exception {
        shift(worker, zone, "timestamptz '2026-11-04 08:00+07'", "timestamptz '2026-11-04 16:00+07'");
        shift(outsider, zone, "timestamptz '2026-11-04 08:00+07'", "timestamptz '2026-11-04 16:00+07'");

        mvc.perform(get("/shifts").param("week", "2026-11-05").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mon 2 Nov 2026 to Sun 8 Nov 2026")))
                .andExpect(content().string(containsString("Wanda Worker")))
                .andExpect(content().string(containsString("08:00 to 16:00")))
                .andExpect(content().string(containsString("(8 h) Mgr Zone")))
                .andExpect(content().string(not(containsString("Otto Outsider"))))
                .andExpect(content().string(containsString("week=2026-10-26")))
                .andExpect(content().string(containsString("week=2026-11-09")));
        mvc.perform(get("/shifts").param("week", "2026-11-09").with(as(manager)))
                .andExpect(content().string(not(containsString("08:00 to 16:00"))));
        mvc.perform(get("/shifts").param("week", "nonsense").with(as(manager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aShiftAppearsUnderTheDayItStarts() throws Exception {
        shift(worker, zone, "timestamptz '2026-11-04 08:00+07'", "timestamptz '2026-11-04 16:00+07'");

        String page = mvc.perform(get("/shifts").param("week", "2026-11-02").with(as(manager)))
                .andReturn().getResponse().getContentAsString();
        String row = page.substring(page.indexOf("Wanda Worker"));

        // Monday and Tuesday are empty, Wednesday has the shift, Thursday is empty again
        assertThat(row).contains("date=2026-11-02", "date=2026-11-03", "date=2026-11-05");
        assertThat(row).doesNotContain("date=2026-11-04");
        assertThat(row.indexOf("date=2026-11-03")).isLessThan(row.indexOf("08:00 to 16:00"));
        assertThat(row.indexOf("08:00 to 16:00")).isLessThan(row.indexOf("date=2026-11-05"));
    }

    @Test
    void anOvernightShiftBelongsToTheDayItStartsAndAMidnightOneToTheNextWeek() throws Exception {
        shift(worker, zone, "timestamptz '2026-11-08 22:00+07'", "timestamptz '2026-11-09 06:00+07'");
        Long other = userWithRole("MGR-W3", "Nina Night", "WORKER");
        joinTeam(other, jdbc.queryForObject("SELECT team_id FROM app_user WHERE id = ?", Long.class, worker));
        shift(other, zone, "timestamptz '2026-11-09 00:00+07'", "timestamptz '2026-11-09 08:00+07'");

        mvc.perform(get("/shifts").param("week", "2026-11-02").with(as(manager)))
                .andExpect(content().string(containsString("22:00 to 06:00")))
                .andExpect(content().string(not(containsString("00:00 to 08:00"))));
        mvc.perform(get("/shifts").param("week", "2026-11-09").with(as(manager)))
                .andExpect(content().string(containsString("00:00 to 08:00")))
                .andExpect(content().string(not(containsString("22:00 to 06:00"))));
    }

    @Test
    void gridTotalsHoursWithoutCancelledShiftsAndMarksThem() throws Exception {
        shift(worker, zone, "timestamptz '2026-11-02 08:00+07'", "timestamptz '2026-11-02 16:00+07'");
        Long cancelled = shift(worker, zone, "timestamptz '2026-11-03 08:00+07'", "timestamptz '2026-11-03 18:00+07'");
        jdbc.update("UPDATE shift SET status = 'CANCELLED' WHERE id = ?", cancelled);

        mvc.perform(get("/shifts").param("week", "2026-11-02").with(as(manager)))
                .andExpect(content().string(containsString("chip-cancelled")))
                .andExpect(content().string(containsString("<td class=\"hours\">8</td>")));
    }

    @Test
    void aDeactivatedWorkerKeepsTheirRowButCannotGetNewShifts() throws Exception {
        shift(worker, zone, "timestamptz '2026-11-02 08:00+07'", "timestamptz '2026-11-02 16:00+07'");
        jdbc.update("UPDATE app_user SET is_active = false WHERE id = ?", worker);

        mvc.perform(get("/shifts").param("week", "2026-11-02").with(as(manager)))
                .andExpect(content().string(containsString("Wanda Worker")))
                .andExpect(content().string(not(containsString("workerId=" + worker))));
        mvc.perform(get("/shifts").param("week", "2026-11-09").with(as(manager)))
                .andExpect(content().string(not(containsString("Wanda Worker"))));
    }

    @Test
    void anEmptyCellOpensTheFormWithItsWorkerAndDate() throws Exception {
        mvc.perform(get("/shifts/new").param("workerId", worker.toString()).param("date", "2026-11-10").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-11-10\"")))
                .andExpect(content().string(containsString("value=\"07:00\"")))     // the morning frame (BR-21)
                .andExpect(content().string(containsString("value=\"11:30\"")));
        mvc.perform(get("/shifts/new").param("date", "nonsense").with(as(manager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aLongGridIsPagedAndKeepsItsWeek() throws Exception {
        Long team = jdbc.queryForObject("SELECT team_id FROM app_user WHERE id = ?", Long.class, worker);
        for (int i = 1; i <= 20; i++) {
            joinTeam(userWithRole("MGR-G" + i, "Grid Person " + i, "WORKER"), team);
        }

        mvc.perform(get("/shifts?week=2026-11-02").with(as(manager)))
                .andExpect(content().string(containsString("Page 1 of 2")))
                .andExpect(content().string(containsString("week=2026-11-02&amp;page=1")));
        mvc.perform(get("/shifts?week=2026-11-02&page=1").with(as(manager)))
                .andExpect(content().string(containsString("Wanda Worker")));
        mvc.perform(get("/shifts?week=2026-11-02&page=9").with(as(manager)))
                .andExpect(status().isOk());
    }

    @Test
    void aManagerWithNoPeopleSeesAMessageInsteadOfAnEmptyGrid() throws Exception {
        Long lonely = userWithRole("MGR-M3", "Lone Manager", "MANAGER");

        mvc.perform(get("/shifts").with(as(lonely)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nobody is on your team yet")))
                .andExpect(content().string(not(containsString("shift-grid"))));
    }

    @Test
    void newShiftOffersOnlyYourTeamAndTakesSiteLocalTime() throws Exception {
        mvc.perform(get("/shifts/new").with(as(manager)))
                .andExpect(content().string(containsString("Wanda Worker (MGR-W1)")))
                .andExpect(content().string(not(containsString("Otto Outsider"))))
                .andExpect(content().string(containsString("Mgr Zone")));

        mvc.perform(post("/shifts").params(form(worker, OPEN_DAY, "07:00", "11:30")).with(as(manager)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/shifts?week=" + OPEN_DAY));

        assertThat(textColumn("SELECT start_at AT TIME ZONE 'UTC' FROM shift WHERE user_id = ?", worker))
                .startsWith(OPEN_DAY + " 00:00");   // 07:00 in Vietnam (UTC+7, BR-25)
        assertThat(textColumn("SELECT created_by::text FROM shift WHERE user_id = ?", worker)).isEqualTo(manager.toString());
    }

    @Test
    void aShiftCannotBeGivenToAnotherTeam() throws Exception {
        mvc.perform(post("/shifts").params(form(outsider, OPEN_DAY, "07:00", "11:30")).with(as(manager)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shift WHERE user_id = ?", Long.class, outsider)).isZero();
    }

    @Test
    void badShiftFormsShowTheFormAgainWithTheProblem() throws Exception {
        mvc.perform(post("/shifts").params(form(worker, OPEN_DAY, "16:00", "08:00")).with(as(manager)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("The end must be after the start and the shift must last 30 minutes to 12 hours.")));
        mvc.perform(post("/shifts").params(form(worker, OPEN_DAY, "07:00", "07:20")).with(as(manager)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("30 minutes to 12 hours")));
        mvc.perform(post("/shifts").param("zoneId", zone.toString()).param("day", OPEN_DAY).param("start", "07:00").param("end", "11:30")
                        .with(as(manager)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Choose a worker")));
        mvc.perform(post("/shifts").param("workerId", worker.toString()).param("zoneId", zone.toString())
                        .with(as(manager)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Enter when the shift starts and ends")));
        mvc.perform(post("/shifts").params(form(worker, "tomorrow", "07:00", "11:30")).with(as(manager)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingAShiftSavesAndAStaleFormIsRefused() throws Exception {
        // A week ahead in site time: a shift that hasn't started yet can't be marked completed.
        String day = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusWeeks(2).toString();
        Long id = shift(worker, zone, "timestamptz '" + day + " 08:00+07'", "timestamptz '" + day + " 16:00+07'");

        mvc.perform(get("/shifts/" + id + "/edit").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + day + "\"")))
                .andExpect(content().string(containsString("value=\"08:00\"")))
                .andExpect(content().string(not(containsString("Mark completed"))))
                .andExpect(content().string(containsString("Cancel shift")));

        var edit = form(worker, day, "09:00", "17:00");
        edit.add("version", "0");
        mvc.perform(post("/shifts/" + id).params(edit).with(as(manager)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(textColumn("SELECT start_at AT TIME ZONE 'UTC' FROM shift WHERE id = ?", id)).startsWith(day + " 02:00");
        assertThat(textColumn("SELECT updated_by::text FROM shift WHERE id = ?", id)).isEqualTo(manager.toString());

        var stale = form(worker, day, "10:00", "17:00");
        stale.add("version", "0");
        mvc.perform(post("/shifts/" + id).params(stale).with(as(manager)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Someone else changed this")));
    }

    @Test
    void anotherManagerCannotOpenOrChangeYourShifts() throws Exception {
        Long id = shift(worker, zone, "timestamptz '2026-11-10 08:00+07'", "timestamptz '2026-11-10 16:00+07'");

        mvc.perform(get("/shifts/" + id + "/edit").with(as(otherManager))).andExpect(status().isForbidden());
        mvc.perform(post("/shifts/" + id + "/cancel").with(as(otherManager)).with(csrf())).andExpect(status().isForbidden());
        assertThat(textColumn("SELECT status FROM shift WHERE id = ?", id)).isEqualTo("SCHEDULED");
    }

    @Test
    void aFinishedShiftIsCompletedByTheSystemAndCanNoLongerBeChanged() throws Exception {
        Long id = shift(worker, zone, "now() - interval '9 hours'", "now() - interval '1 hour'");

        mvc.perform(get("/shifts/" + id + "/edit").with(as(manager)))
                .andExpect(content().string(not(containsString("Mark completed"))))   // nobody marks it by hand
                .andExpect(content().string(not(containsString("Cancel this shift"))));
        siteJobs.hourly(java.time.LocalDateTime.now());
        assertThat(textColumn("SELECT status FROM shift WHERE id = ?", id)).isEqualTo("COMPLETED");
        entityManager.clear();      // the test shares one transaction; the job wrote with SQL

        mvc.perform(get("/shifts/" + id + "/edit").with(as(manager)))
                .andExpect(content().string(containsString("can&#39;t be changed")))
                .andExpect(content().string(containsString("marked this shift completed when it ended")))
                .andExpect(content().string(not(containsString("Save shift"))));
        mvc.perform(post("/shifts/" + id + "/cancel").with(as(manager)).with(csrf()))
                .andExpect(flash().attribute("error", "Only a scheduled shift can be changed"));
    }

    @Test
    void teamShowsYourPeopleOnly() throws Exception {
        mvc.perform(get("/team").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wanda Worker (MGR-W1)")))
                .andExpect(content().string(containsString("Team A")))
                .andExpect(content().string(not(containsString("Otto Outsider"))));
    }

    @Test
    void aManagerCannotEditEvenTheirOwnTeam() throws Exception {
        mvc.perform(get("/team").with(as(manager)))
                .andExpect(content().string(containsString("Add shift")))
                .andExpect(content().string(not(containsString("/team/" + worker + "/edit"))))
                .andExpect(content().string(not(containsString("/team/new"))));
        mvc.perform(get("/team/" + worker + "/edit").with(as(manager))).andExpect(status().isForbidden());
        mvc.perform(post("/team/" + worker).param("fullName", "Changed").param("version", "0").with(as(manager)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(textColumn("SELECT full_name FROM app_user WHERE id = ?", worker)).isEqualTo("Wanda Worker");
    }

    @Test
    void anAdminCanEditSomeonesDetails() throws Exception {
        mvc.perform(get("/team/" + worker + "/edit").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wanda Worker")));

        mvc.perform(post("/team/" + worker).param("fullName", " Wanda W ").param("email", "wanda@example.com")
                        .param("phoneNumber", "0912 345 670").param("version", "0").with(as(admin)).with(csrf()))
                .andExpect(header().string("Location", startsWith("/team")));

        assertThat(textColumn("SELECT full_name FROM app_user WHERE id = ?", worker)).isEqualTo("Wanda W");
        assertThat(textColumn("SELECT email FROM app_user WHERE id = ?", worker)).isEqualTo("wanda@example.com");
        assertThat(textColumn("SELECT phone_number FROM app_user WHERE id = ?", worker)).isEqualTo("0912345670");
    }

    @Test
    void badProfileEditsAreRefused() throws Exception {
        mvc.perform(post("/team/" + worker).param("fullName", " ").param("version", "0").with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("A name is required")));
        mvc.perform(post("/team/" + worker).param("fullName", "Wanda W").param("email", "not-an-email").param("phoneNumber", "0912345670")
                        .param("version", "0").with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("doesn&#39;t look right")));
        mvc.perform(post("/team/" + worker).param("fullName", "Wanda W").param("phoneNumber", "0912345670").param("version", "7").with(as(admin)).with(csrf()))
                .andExpect(status().isConflict());
    }

    /** BR-06, MSG14, MSG15: 10 digits starting with 0, not used by another active account. */
    @Test
    void aPhoneNumberMustHaveTenDigitsAndBeFree() throws Exception {
        jdbc.update("UPDATE app_user SET phone_number = '0987654321' WHERE id = ?", outsider);
        mvc.perform(post("/team/" + worker).param("fullName", "Wanda W").param("phoneNumber", "12345").param("version", "0").with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Phone number: 10 digits starting with 0.")));
    }

    @Test
    void aPhoneNumberOfAnotherActiveAccountIsRefused() throws Exception {
        jdbc.update("UPDATE app_user SET phone_number = '0987654321' WHERE id = ?", outsider);
        mvc.perform(post("/team/" + worker).param("fullName", "Wanda W").param("phoneNumber", "0987654321").param("version", "0").with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("This phone number is used by another employee.")));
    }

    @Test
    void aManagerCannotEditOutsidersOrThemselves() throws Exception {
        mvc.perform(get("/team/" + outsider + "/edit").with(as(manager))).andExpect(status().isForbidden());
        mvc.perform(post("/team/" + outsider).param("fullName", "Hacked").param("version", "0").with(as(manager)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/team/" + manager + "/edit").with(as(manager))).andExpect(status().isForbidden());
        assertThat(textColumn("SELECT full_name FROM app_user WHERE id = ?", outsider)).isEqualTo("Otto Outsider");
    }

    @Test
    void attendanceShowsYourTeamReadOnly() throws Exception {
        Long visit = jdbc.queryForObject("""
                INSERT INTO check_in (user_id, method, recorded_by, check_in_at) VALUES (?, 'MANUAL', ?, now() - interval '1 hour')
                RETURNING id""", Long.class, worker, hse);
        jdbc.update("INSERT INTO check_in (user_id, method, recorded_by, check_in_at) VALUES (?, 'MANUAL', ?, now())", outsider, hse);

        mvc.perform(get("/attendance").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wanda Worker")))
                .andExpect(content().string(not(containsString("Otto Outsider"))))
                .andExpect(content().string(not(containsString("Add a check-in"))))
                .andExpect(content().string(not(containsString("/attendance/" + visit + "/edit"))));
        mvc.perform(get("/attendance/" + visit + "/edit").with(as(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/attendance/new").with(as(manager))).andExpect(status().isForbidden());
    }

    /** BR-31: 45 minutes late is late, with no make-up time; leaving at the shift end is not early. */
    @Test
    void attendanceTagsFollowTheFiveMinuteGraceWithNoMakeUpTime() throws Exception {
        Long shift = shift(worker, zone, "timestamptz '2026-11-02 08:00+07'", "timestamptz '2026-11-02 17:00+07'");
        jdbc.update("""
                INSERT INTO check_in (user_id, shift_id, method, recorded_by, check_in_at, check_out_at)
                VALUES (?, ?, 'MANUAL', ?, timestamptz '2026-11-02 08:45+07', timestamptz '2026-11-02 17:00+07')""",
                worker, shift, hse);

        mvc.perform(get("/attendance").with(as(manager)))
                .andExpect(content().string(containsString("Check-in late (45 min)")))
                .andExpect(content().string(not(containsString("Check-out early"))))
                .andExpect(content().string(not(containsString("late-after"))));
    }

    @Test
    void teamIsPaged() throws Exception {
        Long team = jdbc.queryForObject("SELECT team_id FROM app_user WHERE id = ?", Long.class, worker);
        for (int i = 1; i <= 20; i++) {
            joinTeam(userWithRole("MGR-P" + i, "Paged Person " + i, "WORKER"), team);
        }

        mvc.perform(get("/team").with(as(manager)))
                .andExpect(content().string(containsString("Page 1 of 2")));
        mvc.perform(get("/team").param("page", "1").with(as(manager)))
                .andExpect(content().string(containsString("Wanda Worker")));
    }

    /** The Shift Form of SRS 3.2.10: a day, a start and an end, in site time. */
    private org.springframework.util.LinkedMultiValueMap<String, String> form(Long workerId, String day, String start, String end) {
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("workerId", workerId.toString());
        form.add("zoneId", zone.toString());
        form.add("day", day);
        form.add("start", start);
        form.add("end", end);
        return form;
    }

    /** A Monday far enough ahead that a team leader may still plan its week (BR-23). */
    private static final String OPEN_DAY = "2027-03-08";
}
