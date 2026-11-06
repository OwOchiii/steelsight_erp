package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Role;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.security.AdminBootstrap;
import orochi.steelsight.security.AuthenticatedUser;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class LoginIntegrationTest {

    private static final String PASSWORD = "correct-horse";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwordEncoder;

    AppUser worker;

    @BeforeEach
    void seed() {
        worker = new AppUser();
        worker.setRole(roles.findByRoleName("WORKER").orElseThrow());
        worker.setEmployeeCode("LOGIN-W");
        worker.setFullName("Login Worker");
        worker.setPasswordHash(passwordEncoder.encode(PASSWORD));
        users.saveAndFlush(worker);
    }

    @Test
    void anonymousVisitorIsSentToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Employee code")));
    }

    @Test
    void correctPasswordSignsInWithRoleAndPermissions() throws Exception {
        mvc.perform(formLogin("/login").userParameter("employeeCode").user("LOGIN-W").password(PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated()
                        .withUsername("LOGIN-W")
                        .withAuthentication(auth -> {
                            assertThat(auth.getAuthorities()).extracting("authority")
                                    .contains("ROLE_WORKER", "TICKET_CREATE", "SHIFT_READ_OWN")
                                    .doesNotContain("USER_MANAGE", "VIOLATION_REVIEW");
                            assertThat(((AuthenticatedUser) auth.getPrincipal()).getUserId()).isEqualTo(worker.getId());
                        }));
    }

    @Test
    void wrongPasswordAndUnknownCodeGetTheSameAnswer() throws Exception {
        mvc.perform(formLogin("/login").userParameter("employeeCode").user("LOGIN-W").password("wrong"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        mvc.perform(formLogin("/login").userParameter("employeeCode").user("NOBODY").password(PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void deactivatedUserCannotSignIn() throws Exception {
        worker.setActive(false);
        users.saveAndFlush(worker);

        mvc.perform(formLogin("/login").userParameter("employeeCode").user("LOGIN-W").password(PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void softDeletedUserCannotSignIn() throws Exception {
        worker.setDeletedAt(OffsetDateTime.now());
        users.saveAndFlush(worker);

        mvc.perform(formLogin("/login").userParameter("employeeCode").user("LOGIN-W").password(PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void loginWithoutACsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/login").param("employeeCode", "LOGIN-W").param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void homeGreetsTheUserAndEscapesTheirName() throws Exception {
        worker.setFullName("<script>x</script>");
        users.saveAndFlush(worker);

        mvc.perform(get("/").with(user(new AuthenticatedUser(worker))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        mvc.perform(logout()).andExpect(redirectedUrl("/login?signedOut"));
    }

    @Test
    @WithAnonymousUser
    void bootstrapCreatesTheFirstAdminOnceAndOnlyWithAPassword() {
        Role adminRole = roles.findByRoleName("ADMIN").orElseThrow();
        users.findAllByRoleIdAndDeletedAtIsNull(adminRole.getId()).forEach(admin -> admin.setDeletedAt(OffsetDateTime.now()));
        users.flush();

        bootstrap("FIRST", "");
        assertThat(users.findAllByRoleIdAndDeletedAtIsNull(adminRole.getId())).isEmpty();

        bootstrap("FIRST", "admin-password");
        AppUser admin = users.findByEmployeeCodeAndDeletedAtIsNull("FIRST").orElseThrow();
        assertThat(passwordEncoder.matches("admin-password", admin.getPasswordHash())).isTrue();

        bootstrap("SECOND", "another-password");
        assertThat(users.findByEmployeeCodeAndDeletedAtIsNull("SECOND")).isEmpty();
    }

    private void bootstrap(String code, String password) {
        new AdminBootstrap(users, roles, passwordEncoder, code, password).run(new DefaultApplicationArguments());
    }
}
