package orochi.steelsight;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.security.AuthenticatedUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class PagesIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;

    Long worker;
    Long otherWorker;
    Long hse;
    Long zone;
    Long camera;
    Long safetyConcern;

    @BeforeEach
    void seed() {
        worker = userWithRole("PAGE-W1", "Wanda Worker", "WORKER");
        otherWorker = userWithRole("PAGE-W2", "Otto Other", "WORKER");
        hse = userWithRole("PAGE-H", "Hana Hse", "HSE_OFFICER");
        zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('Page Zone') RETURNING id", Long.class);
        camera = jdbc.queryForObject(
                "INSERT INTO camera (camera_code, zone_id) VALUES ('PAGE-CAM', ?) RETURNING id", Long.class, zone);
        safetyConcern = jdbc.queryForObject(
                "SELECT id FROM ticket_category WHERE category_name = 'Safety concern'", Long.class);
    }

    @Test
    void homeShowsTheWorkersNextShift() throws Exception {
        shift(worker, "now() + interval '1 day'", "now() + interval '1 day 8 hours'");

        mvc.perform(get("/").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome, Wanda Worker")))
                .andExpect(content().string(containsString("Page Zone")));
    }

    @Test
    void timetableShowsOnlyYourOwnShifts() throws Exception {
        shift(worker, "now() + interval '1 day'", "now() + interval '1 day 8 hours'");
        shift(otherWorker, "now() + interval '2 days'", "now() + interval '2 days 2 hours'");

        mvc.perform(get("/timetable").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(8 h)")))
                .andExpect(content().string(not(containsString("(2 h)"))));
    }

    @Test
    void anEmptyTimetableSaysSoInsteadOfFailing() throws Exception {
        mvc.perform(get("/timetable").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have no shifts planned")));
    }

    @Test
    void violationListShowsOnlyYourOwn() throws Exception {
        violation(worker, "NO_VEST");
        violation(otherWorker, "NO_GLOVES");

        mvc.perform(get("/violations").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No vest")))
                .andExpect(content().string(not(containsString("No gloves"))));
    }

    @Test
    void someoneElsesViolationIsForbidden() throws Exception {
        Long theirs = violation(otherWorker, "NO_VEST");

        mvc.perform(get("/violations/" + theirs).with(as(worker)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Not allowed")));
    }

    @Test
    void disputingAViolationOpensATicketAndCannotBeRepeated() throws Exception {
        Long id = violation(worker, "NO_VEST");

        MvcResult result = mvc.perform(post("/violations/" + id + "/dispute")
                        .param("reason", "I was wearing it").with(as(worker)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", Matchers.startsWith("/tickets/")))
                .andReturn();

        assertThat(jdbc.queryForObject("SELECT dispute_status FROM violation WHERE id = ?", String.class, id))
                .isEqualTo("DISPUTED");
        mvc.perform(get(result.getResponse().getRedirectedUrl()).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dispute: No vest")))
                .andExpect(content().string(containsString("I was wearing it")));
        mvc.perform(get("/violations/" + id).with(as(worker)))
                .andExpect(content().string(not(containsString("Send dispute"))));
        mvc.perform(post("/violations/" + id + "/dispute")
                        .param("reason", "again").with(as(worker)).with(csrf()))
                .andExpect(header().string("Location", "/violations/" + id));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket WHERE violation_id = ?", Long.class, id))
                .isEqualTo(1);
    }

    @Test
    void aDisputeNeedsAReason() throws Exception {
        Long id = violation(worker, "NO_VEST");

        mvc.perform(post("/violations/" + id + "/dispute")
                        .param("reason", "   ").with(as(worker)).with(csrf()))
                .andExpect(header().string("Location", "/violations/" + id));

        assertThat(jdbc.queryForObject("SELECT dispute_status FROM violation WHERE id = ?", String.class, id))
                .isEqualTo("NONE");
    }

    @Test
    void youCannotDisputeSomeoneElsesViolation() throws Exception {
        Long theirs = violation(otherWorker, "NO_VEST");

        mvc.perform(post("/violations/" + theirs + "/dispute")
                        .param("reason", "not mine").with(as(worker)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT dispute_status FROM violation WHERE id = ?", String.class, theirs))
                .isEqualTo("NONE");
    }

    @Test
    void newTicketFormOffersZonesAndLeavesOutDisputes() throws Exception {
        mvc.perform(get("/tickets/new").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Safety concern")))
                .andExpect(content().string(containsString("Page Zone")))
                .andExpect(content().string(not(containsString(">PPE dispute<"))));
    }

    @Test
    void aSafetyConcernKeepsItsZoneAndPhotoAndThePhotoStaysPrivate() throws Exception {
        MvcResult result = mvc.perform(multipart("/tickets")
                        .file(new MockMultipartFile("photo", "../../evil.exe", "text/html", PNG))
                        .param("categoryId", safetyConcern.toString())
                        .param("zoneId", zone.toString())
                        .param("subject", "Broken guard rail")
                        .param("description", "Loose on level 2")
                        .with(as(worker)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String url = result.getResponse().getRedirectedUrl();

        String stored = jdbc.queryForObject("SELECT photo_path FROM ticket WHERE subject = 'Broken guard rail'", String.class);
        assertThat(stored).endsWith(".png").doesNotContain("evil");

        mvc.perform(get(url).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Page Zone")))
                .andExpect(content().string(containsString(url + "/photo")));
        mvc.perform(get(url + "/photo").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        mvc.perform(get(url + "/photo").with(as(otherWorker))).andExpect(status().isForbidden());
    }

    @Test
    void aFileThatIsNotAnImageIsRefused() throws Exception {
        mvc.perform(multipart("/tickets")
                        .file(new MockMultipartFile("photo", "photo.png", "image/png", "<script>".getBytes()))
                        .param("categoryId", safetyConcern.toString())
                        .param("subject", "Not an image")
                        .param("description", "details")
                        .with(as(worker)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("JPEG, PNG or WebP")))
                .andExpect(content().string(containsString("Not an image")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket WHERE subject = 'Not an image'", Long.class)).isZero();
    }

    @Test
    void aBlankTicketShowsTheFormAgainWithTheProblem() throws Exception {
        mvc.perform(multipart("/tickets")
                        .param("categoryId", safetyConcern.toString())
                        .param("subject", "  ")
                        .param("description", "Kept text")
                        .with(as(worker)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("A subject is required")))
                .andExpect(content().string(containsString("Kept text")));
    }

    @Test
    void aTamperedCategoryIsRefusedWithoutAServerError() throws Exception {
        mvc.perform(multipart("/tickets")
                        .param("categoryId", "abc")
                        .param("subject", "s").param("description", "d")
                        .with(as(worker)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageTextIsEscaped() throws Exception {
        Long ticket = ticket(worker, "<script>alert(1)</script>");

        mvc.perform(get("/tickets/" + ticket).with(as(worker)))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
        mvc.perform(get("/tickets").with(as(worker)))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    void youCanCommentOnYourOwnTicketButNotSomeoneElses() throws Exception {
        Long ticket = ticket(worker, "Mine");

        mvc.perform(post("/tickets/" + ticket + "/comments")
                        .param("body", "Any news?").with(as(worker)).with(csrf()))
                .andExpect(header().string("Location", "/tickets/" + ticket));
        mvc.perform(get("/tickets/" + ticket).with(as(worker)))
                .andExpect(content().string(containsString("Any news?")));

        mvc.perform(get("/tickets/" + ticket).with(as(otherWorker))).andExpect(status().isForbidden());
        mvc.perform(post("/tickets/" + ticket + "/comments")
                        .param("body", "Sneaky").with(as(otherWorker)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void formsWithoutACsrfTokenAreRefused() throws Exception {
        Long ticket = ticket(worker, "Mine");

        mvc.perform(post("/tickets/" + ticket + "/comments").param("body", "x").with(as(worker)))
                .andExpect(status().isForbidden());
    }

    @Test
    void menuAndPagesFollowTheRolesPermissions() throws Exception {
        mvc.perform(get("/").with(as(worker)))
                .andExpect(content().string(containsString("href=\"/violations\"")));

        // HSE officers review every violation but have none of their own in this menu
        mvc.perform(get("/").with(as(hse)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/violations\""))));
        mvc.perform(get("/violations").with(as(hse))).andExpect(status().isForbidden());
    }

    @Test
    void aMissingOrMalformedIdIsNotFound() throws Exception {
        mvc.perform(get("/tickets/abc").with(as(worker))).andExpect(status().isNotFound());
        mvc.perform(get("/violations/999999999").with(as(worker))).andExpect(status().isNotFound());
    }

    private RequestPostProcessor as(Long userId) {
        return user(new AuthenticatedUser(users.findById(userId).orElseThrow()));
    }

    private Long userWithRole(String code, String name, String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
                SELECT id, ?, ?, 'x' FROM role WHERE role_name = ? RETURNING id""", Long.class, code, name, role);
    }

    private void shift(Long user, String start, String end) {
        jdbc.update("INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, "
                + start + ", " + end + ", ?)", user, zone, user);
    }

    private Long violation(Long user, String type) {
        return jdbc.queryForObject("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, ?, now(), 'page.jpg') RETURNING id""", Long.class, user, camera, type);
    }

    private Long ticket(Long creator, String subject) {
        return jdbc.queryForObject("""
                INSERT INTO ticket (category_id, subject, description, created_by)
                VALUES (?, ?, 'details', ?) RETURNING id""", Long.class, safetyConcern, subject, creator);
    }
}
