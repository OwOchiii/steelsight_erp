package orochi.steelsight;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Really commits: the database refuses the overlap, which would abort a test's wrapping transaction
 * before the page could be shown again. Cleans up so the rolled-back tests never see this data.
 */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class ShiftOverlapPageTest extends PageTestSupport {

    Long manager;
    Long worker;
    Long teamId;
    Long zoneId;

    @BeforeEach
    void seed() {
        manager = userWithRole("OVER-M", "Over Manager", "MANAGER");
        worker = userWithRole("OVER-W", "Over Worker", "WORKER");
        teamId = team("Over Team", manager);
        joinTeam(worker, teamId);
        zoneId = zone("Over Zone");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shift WHERE user_id = ?", worker);
        jdbc.update("UPDATE app_user SET team_id = NULL WHERE id = ?", worker);
        jdbc.update("DELETE FROM team WHERE id = ?", teamId);
        jdbc.update("DELETE FROM app_user WHERE id IN (?, ?)", worker, manager);
        jdbc.update("DELETE FROM zone WHERE id = ?", zoneId);
    }

    @Test
    void anOverlappingShiftShowsTheFormAgainWithTheProblem() throws Exception {
        // A Monday far enough ahead that the team leader may still plan it (BR-23)
        shift(worker, zoneId, "timestamptz '2027-03-08 07:00+07'", "timestamptz '2027-03-08 11:30+07'");

        mvc.perform(post("/shifts").param("workerId", worker.toString()).param("zoneId", zoneId.toString())
                        .param("day", "2027-03-08").param("start", "10:00").param("end", "14:00")
                        .with(as(manager)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("This person already has a shift from")))
                .andExpect(content().string(containsString("Over Worker (OVER-W)")));
    }
}
