package orochi.steelsight;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class TeamsTicketsAndReportTest extends PageTestSupport {

    Long admin;
    Long leader;
    Long otherLeader;
    Long freeManager;
    Long worker;
    Long otherWorker;
    Long hse;
    Long teamR;
    Long teamS;
    Long zone;
    Long camera;

    @BeforeEach
    void seed() {
        admin = userWithRole("TTR-A", "Ada Admin", "ADMIN");
        leader = userWithRole("TTR-M1", "Lena Leader", "MANAGER");
        otherLeader = userWithRole("TTR-M2", "Oscar Other", "MANAGER");
        freeManager = userWithRole("TTR-M3", "Fred Free", "MANAGER");
        worker = userWithRole("TTR-W1", "Wanda Worker", "WORKER");
        otherWorker = userWithRole("TTR-W2", "Otto Outsider", "WORKER");
        hse = userWithRole("TTR-H", "Hana Hse", "HSE_OFFICER");
        teamR = team("Team R", leader);
        teamS = team("Team S", otherLeader);
        joinTeam(worker, teamR);
        joinTeam(otherWorker, teamS);
        zone = zone("TTR Zone");
        camera = camera("TTR-CAM", zone);
    }

    // --- teams -----------------------------------------------------------------------------------

    @Test
    void adminSetsUpTeamsWithOneLeaderEach() throws Exception {
        mvc.perform(get("/team").with(as(admin)))
                .andExpect(content().string(containsString("Fred Free")))
                .andExpect(content().string(containsString("action=\"/teams\"")));

        mvc.perform(post("/teams").param("name", "Team New").param("managerId", freeManager.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The team was created."));
        Long created = jdbc.queryForObject("SELECT id FROM team WHERE name = 'Team New'", Long.class);

        mvc.perform(post("/teams").param("name", "Team Two").param("managerId", leader.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "This manager already leads another team"));
        mvc.perform(post("/teams").param("name", "Team Two").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "Choose a leader for the team"));
        mvc.perform(post("/teams/" + teamS + "/leader").param("managerId", freeManager.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "This manager already leads another team"));
        mvc.perform(post("/teams/" + teamS + "/leader").param("managerId", otherLeader.toString()).with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The team leader was changed."));

        mvc.perform(post("/teams/" + teamR + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "Move this team's people to another team first"));
        mvc.perform(post("/teams/" + created + "/delete").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The team was deleted."));
        flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team WHERE id = ?", Integer.class, created)).isZero();
    }

    @Test
    void theDatabaseAlsoRefusesALeaderForTwoTeams() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> team("Team Sneaky", leader))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void onlyAdminsSetUpTeams() throws Exception {
        mvc.perform(get("/team").with(as(leader)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("action=\"/teams\""))));
        mvc.perform(post("/teams").param("name", "Mine").param("managerId", freeManager.toString()).with(as(leader)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/teams/" + teamS + "/leader").param("managerId", freeManager.toString()).with(as(leader)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/teams/" + teamS + "/delete").with(as(leader)).with(csrf())).andExpect(status().isForbidden());
    }

    // --- tickets ---------------------------------------------------------------------------------

    @Test
    void safetyTicketsGoToHseAndEverythingElseToAdmin() throws Exception {
        Long leave = ticket(category("Leave request"), worker, "Day off on Friday");
        Long boots = ticket(category("Equipment request"), worker, "New boots");
        ticket(category("Safety concern"), worker, "Loose guard rail");

        mvc.perform(get("/tickets/queue").with(as(admin)))
                .andExpect(content().string(containsString("Day off on Friday")))
                .andExpect(content().string(containsString("New boots")))
                .andExpect(content().string(not(containsString("Loose guard rail"))));
        mvc.perform(get("/tickets/queue").param("all", "true").with(as(admin)))
                .andExpect(content().string(containsString("Loose guard rail")));
        mvc.perform(get("/tickets/queue").with(as(hse)))
                .andExpect(content().string(containsString("Loose guard rail")))
                .andExpect(content().string(not(containsString("Day off on Friday"))));
        mvc.perform(get("/tickets/queue").param("all", "true").with(as(hse)))
                .andExpect(content().string(not(containsString("Day off on Friday"))));

        mvc.perform(get("/tickets/" + leave).with(as(hse))).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + leave + "/resolve").with(as(admin)).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(textColumn("SELECT status FROM ticket WHERE id = ?", leave)).isEqualTo("RESOLVED");
        mvc.perform(get("/tickets/" + boots).with(as(leader))).andExpect(status().isForbidden());
    }

    @Test
    void teamLeadersNoLongerHandleTickets() throws Exception {
        mvc.perform(get("/").with(as(leader)))
                .andExpect(content().string(not(containsString("href=\"/tickets/queue\""))))
                .andExpect(content().string(containsString("href=\"/tickets/new\"")));
        mvc.perform(get("/tickets/queue").with(as(leader))).andExpect(status().isForbidden());
        Long boots = ticket(category("Equipment request"), worker, "New boots");
        mvc.perform(post("/tickets/" + boots + "/resolve").with(as(leader)).with(csrf())).andExpect(status().isForbidden());
    }

    // --- monthly report ----------------------------------------------------------------------------

    /** Last month, so every shift in it is over and counts. */
    private YearMonth seedLastMonth() {
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMonths(1);
        String day3 = month.atDay(3).toString();
        String day4 = month.atDay(4).toString();
        String day5 = month.atDay(5).toString();

        // Late by 30 minutes, then left at 16:00: 75 minutes before the required 17:15.
        Long worked = shift(worker, zone, "timestamptz '" + day3 + " 08:00+07'", "timestamptz '" + day3 + " 17:00+07'");
        jdbc.update("""
                INSERT INTO check_in (user_id, shift_id, zone_id, check_in_at, check_out_at, method, recorded_by)
                VALUES (?, ?, ?, timestamptz '%s 08:30+07', timestamptz '%s 16:00+07', 'MANUAL', ?)""".formatted(day3, day3),
                worker, worked, zone, admin);
        shift(worker, zone, "timestamptz '" + day4 + " 08:00+07'", "timestamptz '" + day4 + " 17:00+07'");
        Long cancelled = shift(worker, zone, "timestamptz '" + day5 + " 08:00+07'", "timestamptz '" + day5 + " 17:00+07'");
        jdbc.update("UPDATE shift SET status = 'CANCELLED' WHERE id = ?", cancelled);

        jdbc.update("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, 'NO_VEST', timestamptz '%s 10:00+07', 'r1.jpg')""".formatted(day3), worker, camera);
        jdbc.update("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path,
                                       dispute_status, reviewed_by, reviewed_at)
                VALUES (?, ?, 'NO_HARD_HAT', timestamptz '%s 11:00+07', 'r2.jpg', 'OVERTURNED', ?, now())""".formatted(day3),
                worker, camera, hse);
        jdbc.update("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, 'NO_VEST', timestamptz '%s 10:00+07', 'r3.jpg')""".formatted(day3), otherWorker, camera);
        return month;
    }

    private Map<String, List<Row>> sheets(byte[] file) throws Exception {
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file))) {
            return workbook.getSheets().collect(Collectors.toMap(Sheet::getName, sheet -> {
                try {
                    return sheet.read();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }));
        }
    }

    private Row rowFor(List<Row> rows, String code) {
        return rows.stream().filter(r -> code.equals(r.getCellText(0)) || code.equals(r.getCellText(2)))
                .findFirst().orElseThrow();
    }

    private BigDecimal number(Row row, int column) {
        return row.getCellAsNumber(column).orElseThrow();
    }

    @Test
    void aLeaderExportsTheirTeamsMonthWithTheRightFigures() throws Exception {
        YearMonth month = seedLastMonth();

        MockHttpServletResponse response = mvc.perform(get("/team/report").param("month", month.toString()).with(as(leader)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")))
                .andExpect(header().string("Content-Disposition", containsString("steelsight-team-r-" + month + ".xlsx")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse();

        Map<String, List<Row>> sheets = sheets(response.getContentAsByteArray());
        assertThat(sheets.keySet()).containsExactlyInAnyOrder("Summary", "Violations", "Attendance", "No-shows");

        List<Row> summary = sheets.get("Summary");
        assertThat(summary.stream().map(r -> r.getCellText(0))).contains("Team: Team R").doesNotContain("TTR-W2");
        Row wanda = rowFor(summary, "TTR-W1");
        assertThat(wanda.getCellText(1)).isEqualTo("Wanda Worker");
        assertThat(number(wanda, 3)).isEqualByComparingTo("90");     // one counted violation; the overturned one gives its points back
        assertThat(wanda.getCellText(4)).isEqualTo("Safe");
        assertThat(number(wanda, 5)).isEqualByComparingTo("1");      // violations
        assertThat(number(wanda, 6)).isEqualByComparingTo("0");      // no hard hat: overturned
        assertThat(number(wanda, 7)).isEqualByComparingTo("1");      // no vest
        assertThat(number(wanda, 10)).isEqualByComparingTo("2");     // shifts scheduled, the cancelled one left out
        assertThat(number(wanda, 11)).isEqualByComparingTo("1");     // worked
        assertThat(number(wanda, 12)).isEqualByComparingTo("1");     // no-shows
        assertThat(number(wanda, 13)).isEqualByComparingTo("7.5");   // hours
        assertThat(number(wanda, 14)).isEqualByComparingTo("1");
        assertThat(number(wanda, 15)).isEqualByComparingTo("30");
        assertThat(number(wanda, 16)).isEqualByComparingTo("1");
        assertThat(number(wanda, 17)).isEqualByComparingTo("75");

        assertThat(sheets.get("Violations").stream().skip(1).map(r -> r.getCellText(1))).containsExactly("TTR-W1", "TTR-W1");
        assertThat(sheets.get("Attendance")).hasSize(2);
        assertThat(sheets.get("No-shows")).hasSize(2);
        assertThat(rowFor(sheets.get("No-shows"), "TTR-W1").getCellText(3)).isEqualTo("Wanda Worker");
    }

    @Test
    void anAdminExportsEveryWorker() throws Exception {
        YearMonth month = seedLastMonth();

        byte[] file = mvc.perform(get("/team/report").param("month", month.toString()).with(as(admin)))
                .andExpect(header().string("Content-Disposition", containsString("steelsight-all-workers-" + month + ".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        List<Row> summary = sheets(file).get("Summary");
        assertThat(rowFor(summary, "TTR-W2").getCellText(2)).isEqualTo("Team S");
        assertThat(rowFor(summary, "TTR-W1")).isNotNull();
    }

    @Test
    void theReportIsOnlyForLeadersAndAdmins() throws Exception {
        mvc.perform(get("/team/report").with(as(worker))).andExpect(status().isForbidden());
        mvc.perform(get("/team/report").with(as(hse))).andExpect(status().isForbidden());
        mvc.perform(get("/team/report").with(as(freeManager)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("You don&#39;t lead a team yet")));
        mvc.perform(get("/team/report").param("month", YearMonth.now().plusMonths(2).toString()).with(as(leader)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/team/report").param("month", "not-a-month").with(as(leader)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/team").with(as(leader))).andExpect(content().string(containsString("Download Excel")));
        mvc.perform(get("/team").with(as(freeManager))).andExpect(content().string(not(containsString("Download Excel"))));
    }

    @Test
    void theReportIsInVietnameseForAVietnameseReader() throws Exception {
        byte[] file = mvc.perform(get("/team/report").with(as(leader))
                        .cookie(new jakarta.servlet.http.Cookie("steelsight-language", "vi")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(sheets(file).keySet()).contains("Tổng hợp", "Chấm công");
    }
}
