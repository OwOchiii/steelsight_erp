package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {"steelsight.storage.dir=target/test-uploads", "steelsight.detection.api-key=test-key"})
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class DetectionAlertApiTest extends PageTestSupport {

    private static final String PATH = "/api/v1/detection/alerts";

    @Test
    void aMissingOrWrongKeyIsRefusedAsJson() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
        mvc.perform(post(PATH).header("X-Api-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
        assertThat(alerts()).isZero();
    }

    @Test
    void anAlertTellsEveryActiveHseOfficerAndNobodyElse() throws Exception {
        Long hse = userWithRole("API-H1", "Hana Hse", "HSE_OFFICER");
        Long hse2 = userWithRole("API-H2", "Hai Hse", "HSE_OFFICER");
        Long inactive = userWithRole("API-H3", "Ina Hse", "HSE_OFFICER");
        jdbc.update("UPDATE app_user SET is_active = false WHERE id = ?", inactive);
        Long worker = userWithRole("API-W", "Wanda Worker", "WORKER");

        send(UUID.randomUUID()).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("created"));

        assertThat(notices(hse)).isEqualTo(1);
        assertThat(notices(hse2)).isEqualTo(1);
        assertThat(notices(inactive)).isZero();
        assertThat(notices(worker)).isZero();
        mvc.perform(get("/notifications").with(as(hse)))
                .andExpect(content().string(containsString("Too many PPE violations")))
                .andExpect(content().string(containsString("7 violations in 60 seconds")))
                .andExpect(content().string(containsString("CAM-B2")));
    }

    @Test
    void aRetryOfTheSameEventChangesNothing() throws Exception {
        Long hse = userWithRole("API-H1", "Hana Hse", "HSE_OFFICER");
        UUID event = UUID.randomUUID();

        send(event).andExpect(status().isCreated());
        send(event).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));

        assertThat(alerts()).isEqualTo(1);
        assertThat(notices(hse)).isEqualTo(1);
    }

    @Test
    void anotherAlertSoonAfterIsKeptWithoutTellingHseAgain() throws Exception {
        Long hse = userWithRole("API-H1", "Hana Hse", "HSE_OFFICER");

        send(UUID.randomUUID()).andExpect(status().isCreated());
        send(UUID.randomUUID()).andExpect(status().isCreated());

        assertThat(alerts()).isEqualTo(2);
        assertThat(notices(hse)).isEqualTo(1);
    }

    @Test
    void aSteadyStreamOfAlertsStillTellsHseAgainAfterTheQuietTime() throws Exception {
        Long hse = userWithRole("API-H1", "Hana Hse", "HSE_OFFICER");
        send(UUID.randomUUID()).andExpect(status().isCreated());
        jdbc.update("UPDATE safety_alert SET received_at = received_at - interval '20 minutes'");
        // This one arrives in the quiet time of nothing, but a silent alert must not restart the clock.
        jdbc.update("""
                INSERT INTO safety_alert (event_id, violation_count, window_seconds, detected_at, notified, received_at)
                VALUES (?, 3, 60, now(), false, now())""", UUID.randomUUID());

        send(UUID.randomUUID()).andExpect(status().isCreated());

        assertThat(notices(hse)).isEqualTo(2);
    }

    @Test
    void aBadBodyIsRefusedAsJson() throws Exception {
        for (String body : new String[]{
                "not json",
                "{}",
                "{\"eventId\":\"not-a-uuid\",\"violationCount\":7,\"windowSeconds\":60,\"detectedAt\":\"2026-11-05T09:30:00+07:00\"}",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"violationCount\":0,\"windowSeconds\":60,\"detectedAt\":\"2026-11-05T09:30:00+07:00\"}",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"violationCount\":7,\"windowSeconds\":0,\"detectedAt\":\"2026-11-05T09:30:00+07:00\"}",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"violationCount\":7,\"windowSeconds\":60}"}) {
            mvc.perform(post(PATH).header("X-Api-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
        assertThat(alerts()).isZero();
    }

    @Test
    void theCameraCodeIsOptional() throws Exception {
        mvc.perform(post(PATH).header("X-Api-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"eventId\":\"" + UUID.randomUUID() + "\",\"violationCount\":7,\"windowSeconds\":60,\"detectedAt\":\"2026-11-05T09:30:00+07:00\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void pagesStillRequireACsrfToken() throws Exception {
        Long hse = userWithRole("API-H1", "Hana Hse", "HSE_OFFICER");
        mvc.perform(post("/notifications/read-all").with(as(hse))).andExpect(status().isForbidden());
    }

    private ResultActions send(UUID event) throws Exception {
        return mvc.perform(post(PATH).header("X-Api-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body(event)));
    }

    private String body(UUID event) {
        return "{\"eventId\":\"" + event + "\",\"violationCount\":7,\"windowSeconds\":60,"
                + "\"detectedAt\":\"2026-11-05T09:30:00+07:00\",\"cameraCode\":\"CAM-B2\"}";
    }

    private Integer alerts() {
        return jdbc.queryForObject("SELECT count(*)::int FROM safety_alert", Integer.class);
    }

    private Integer notices(Long user) {
        return jdbc.queryForObject("SELECT count(*)::int FROM notification WHERE user_id = ?", Integer.class, user);
    }
}
