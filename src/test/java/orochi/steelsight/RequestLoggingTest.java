package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestDatabase.class, RequestLoggingTest.BrokenPage.class})
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingTest {

    @RestController
    static class BrokenPage {
        @GetMapping("/test-broken")
        String broken() {
            throw new IllegalStateException("the database fell over");
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void anUnexpectedFailureIsLoggedWithItsStackTraceAndAReferenceTheUserCanQuote(CapturedOutput output) throws Exception {
        String page = mvc.perform(get("/test-broken").with(user("LOG-1")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("reference ")))
                .andReturn().getResponse().getContentAsString();

        String reference = page.replaceAll("(?s).*reference ([0-9a-f]{8}).*", "$1");
        assertThat(output).contains("java.lang.IllegalStateException: the database fell over");
        assertThat(output).contains("[" + reference + "]");
        assertThat(output).contains("GET /test-broken -> 500 user=LOG-1");
        assertThat(page).doesNotContain("the database fell over");
    }

    @Test
    void anUnknownAddressIsStillNotFoundAndNotABug(CapturedOutput output) throws Exception {
        mvc.perform(get("/no-such-page").with(user("LOG-2"))).andExpect(status().isNotFound());

        assertThat(output).contains("GET /no-such-page -> 404 user=LOG-2");
        assertThat(output).doesNotContain("Unexpected failure");
    }

    @Test
    void aRefusedRequestIsLoggedEvenBeforeSignIn(CapturedOutput output) throws Exception {
        mvc.perform(get("/tickets")).andExpect(status().is3xxRedirection());

        assertThat(output).contains("GET /tickets -> 302 user=anonymous");
    }

    @Test
    void aFailedSignInIsLoggedWithoutThePassword(CapturedOutput output) throws Exception {
        mvc.perform(formLogin().userParameter("employeeCode").user("NO-SUCH-CODE").password("hunter2-secret"));

        assertThat(output).contains("Sign-in refused for NO-SUCH-CODE");
        assertThat(output).doesNotContain("hunter2-secret");
    }
}
