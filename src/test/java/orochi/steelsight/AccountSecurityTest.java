package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class AccountSecurityTest extends PageTestSupport {

    private static final String CODE = "SESSION-W";
    private static final String PASSWORD = "correct-horse";

    @Autowired PasswordEncoder passwordEncoder;

    Long worker;

    @BeforeEach
    void seed() {
        worker = userWithRole(CODE, "Session Worker", "WORKER");
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), worker);
    }

    @Test
    void changingYourPasswordEndsYourOtherSessionsButNotThisOne() throws Exception {
        MockHttpSession thisDevice = signIn();
        MockHttpSession otherDevice = signIn();

        mvc.perform(post("/account/password").session(thisDevice).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "a-brand-new-password")
                        .param("confirmPassword", "a-brand-new-password"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(thisDevice)).andExpect(status().isOk());
        mvc.perform(get("/").session(otherDevice)).andExpect(redirectedUrl("/login?signedOut"));
    }

    @Test
    void aFailedPasswordChangeEndsNothing() throws Exception {
        MockHttpSession thisDevice = signIn();
        MockHttpSession otherDevice = signIn();

        mvc.perform(post("/account/password").session(thisDevice).with(csrf())
                        .param("currentPassword", "not-the-password")
                        .param("newPassword", "a-brand-new-password")
                        .param("confirmPassword", "a-brand-new-password"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/").session(otherDevice)).andExpect(status().isOk());
    }

    @Test
    void anAdminResetEndsEverySessionOfThatPersonOnly() throws Exception {
        Long admin = userWithRole("SESSION-A", "Session Admin", "ADMIN");
        Long bystander = userWithRole("SESSION-B", "Bystander", "WORKER");
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), bystander);
        MockHttpSession target = signIn();
        MockHttpSession other = signIn("SESSION-B");

        mvc.perform(post("/team/" + worker + "/password").with(as(admin)).with(csrf())
                        .param("newPassword", "a-temporary-password"))
                .andExpect(redirectedUrl("/team/" + worker + "/edit"));

        mvc.perform(get("/").session(target)).andExpect(redirectedUrl("/login?signedOut"));
        mvc.perform(get("/").session(other)).andExpect(status().isOk());
    }

    @Test
    void apiDocsAreNotServed() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html"}) {
            mvc.perform(get(path).with(as(worker))).andExpect(status().isNotFound());
        }
    }

    private MockHttpSession signIn() throws Exception {
        return signIn(CODE);
    }

    private MockHttpSession signIn(String code) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").userParameter("employeeCode").user(code).password(PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }
}
