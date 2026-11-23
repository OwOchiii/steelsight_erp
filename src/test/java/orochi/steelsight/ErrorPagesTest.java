package orochi.steelsight;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A mistake is shown on the page it was made on, or on the error page, and never as raw JSON. */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class ErrorPagesTest extends PageTestSupport {

    /** A JPEG header followed by padding, just over the 5 MB limit. */
    private static final byte[] LARGE_JPEG = largeJpeg(5 * 1024 * 1024 + 1);

    Long worker;
    Long admin;
    Long safetyConcern;

    @BeforeEach
    void seed() {
        worker = userWithRole("ERR-W", "Wes Worker", "WORKER");
        admin = userWithRole("ERR-A", "Ada Admin", "ADMIN");   // the Admin handles every ticket
        safetyConcern = category("Safety concern");
    }

    @Test
    void aPhotoOverTheLimitShowsTheTicketFormAgainWithWhatWasTyped() throws Exception {
        mvc.perform(multipart("/tickets")
                        .file(new MockMultipartFile("photo", "big.jpg", "image/jpeg", LARGE_JPEG))
                        .param("categoryId", safetyConcern.toString())
                        .param("subject", "Kept subject")
                        .param("description", "Kept details")
                        .with(as(worker)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("The photo must be 5 MB or smaller")))
                .andExpect(content().string(containsString("Kept subject")))
                .andExpect(content().string(containsString("Kept details")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket WHERE subject = 'Kept subject'", Long.class)).isZero();
    }

    @Test
    void thePhotoInputsKnowTheLimitSoTheBrowserCanCheckFirst() throws Exception {
        mvc.perform(get("/tickets/new").with(as(worker)))
                .andExpect(content().string(containsString("data-max-bytes=\"5242880\"")))
                .andExpect(content().string(containsString("data-too-large=\"This photo is larger than 5 MB")));
    }

    @Test
    void aBadValueSentByAFormGoesBackToThatPageWithTheProblem() throws Exception {
        Long ticket = ticket(category("Safety concern"), worker, "Back to me");
        String page = "/tickets/" + ticket;

        mvc.perform(post(page + "/assignee").param("assigneeId", "abc")
                        .header("Referer", "http://localhost" + page)
                        .with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("error", "One of the values isn't valid. Check the form and try again."));

        // Without a page to go back to, the error page says the same
        mvc.perform(post(page + "/assignee").param("assigneeId", "abc").with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("One of the values isn&#39;t valid")));

        // A bad id in the address is still a page that doesn't exist
        mvc.perform(post("/tickets/abc/comments").param("body", "x").with(as(admin)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void goingBackNeverLeavesTheSite() throws Exception {
        String assign = "/tickets/" + ticket(category("Safety concern"), worker, "Stay here") + "/assignee";

        // Only the path is kept, never the host
        mvc.perform(post(assign).param("assigneeId", "abc").header("Referer", "https://evil.example/tickets?x={y}")
                        .with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/tickets?x=%7By%7D"));
        // The URL parser folds a doubled slash, so this lands on this site's /x
        mvc.perform(post(assign).param("assigneeId", "abc").header("Referer", "https://evil.example//x")
                        .with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/x"));
        // Paths a browser would read as another site get the error page instead
        for (String referer : new String[]{"https://evil.example/\\x", "javascript:alert(1)"}) {
            mvc.perform(post(assign).param("assigneeId", "abc").header("Referer", referer).with(as(admin)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().doesNotExist("Location"));
        }
    }

    @Test
    void anUploadOverTheCeilingGetsTheErrorPageNotJson() throws Exception {
        mvc.perform(get("/error").accept("*/*")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 413)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/tickets"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Photo too large")))
                .andExpect(content().string(not(containsString("\"status\""))));
    }

    @Test
    void aFormWithoutItsSecurityTokenIsRefusedWithTheReasonKept() throws Exception {
        mvc.perform(post("/tickets/1/comments").param("body", "x").with(as(worker)))
                .andExpect(status().isForbidden())
                .andExpect(request().attribute(WebAttributes.ACCESS_DENIED_403, instanceOf(CsrfException.class)));
    }

    @Test
    void anExpiredFormSaysSoInsteadOfForbidden() throws Exception {
        mvc.perform(get("/error").accept("*/*")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/tickets")
                        .requestAttr(WebAttributes.ACCESS_DENIED_403, new MissingCsrfTokenException("none")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("This page has expired")));
    }

    @Test
    void theApiStillGetsJson() throws Exception {
        mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/detections"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string("{\"error\":\"The request was rejected\"}"));
    }

    private static byte[] largeJpeg(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 0);
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
