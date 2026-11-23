package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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

    /**
     * SRS CR-02: burst alerts are gone; every violation alerts the HSE Officers on its own. The current
     * detection app still sends them, so they are kept (its outbox stops retrying) but tell nobody.
     */
    @Test
    void aBurstAlertIsKeptButTellsNobody() throws Exception {
        Long hse = userWithRole("API-H1", "Hana Hse", "HSE_OFFICER");
        Long admin = userWithRole("API-A1", "Ada Admin", "ADMIN");

        send(UUID.randomUUID()).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("created"));

        assertThat(alerts()).isEqualTo(1);
        assertThat(notices(hse)).isZero();
        assertThat(notices(admin)).isZero();
    }

    @Test
    void aRetryOfTheSameEventChangesNothing() throws Exception {
        UUID event = UUID.randomUUID();

        send(event).andExpect(status().isCreated());
        send(event).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));

        assertThat(alerts()).isEqualTo(1);
    }

    @Test
    void aBadBodyIsRefusedAsJson() throws Exception {
        for (String body : new String[]{
                "not json",
                "{}",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"violationCount\":100001,\"windowSeconds\":60,\"detectedAt\":\"2026-11-05T09:30:00+07:00\"}",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"violationCount\":7,\"windowSeconds\":86401,\"detectedAt\":\"2026-11-05T09:30:00+07:00\"}",
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

    private ResultActions send(UUID event, String camera, OffsetDateTime detectedAt) throws Exception {
        return mvc.perform(post(PATH).header("X-Api-Key", "test-key").contentType(MediaType.APPLICATION_JSON)
                .content(body(event, camera, detectedAt)));
    }

    private String body(UUID event) {
        return body(event, "CAM-B2", OffsetDateTime.now());
    }

    private String body(UUID event, String camera, OffsetDateTime detectedAt) {
        return "{\"eventId\":\"" + event + "\",\"violationCount\":7,\"windowSeconds\":60,"
                + "\"detectedAt\":\"" + detectedAt + "\",\"cameraCode\":\"" + camera + "\"}";
    }

    private void camera(String code) {
        camera(code, zone("Zone " + code));
    }

    private Integer alerts() {
        return jdbc.queryForObject("SELECT count(*)::int FROM safety_alert", Integer.class);
    }

    private Integer notices(Long user) {
        return jdbc.queryForObject("SELECT count(*)::int FROM notification WHERE user_id = ?", Integer.class, user);
    }
}
