package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class DeploymentEndpointsTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void healthIsPublicAndShowsNoDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(content().string(not(containsString("components"))));
    }

    @Test
    void otherActuatorPathsStayClosed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/actuator")).andExpect(status().is3xxRedirection());
    }

    @Test
    void repeatedWrongPasswordsLockTheSignInEvenWithTheRightPassword() throws Exception {
        String code = "LOCK-" + UUID.randomUUID().toString().substring(0, 8);
        AppUser worker = new AppUser();
        worker.setRole(roles.findByRoleName("WORKER").orElseThrow());
        worker.setEmployeeCode(code);
        worker.setFullName("Lock Test");
        worker.setPasswordHash(passwordEncoder.encode("right-password"));
        users.saveAndFlush(worker);

        for (int i = 0; i < 5; i++) {
            mvc.perform(formLogin().userParameter("employeeCode").user(code).password("wrong"))
                    .andExpect(redirectedUrl("/login?error"))
                    .andExpect(unauthenticated());
        }

        mvc.perform(formLogin().userParameter("employeeCode").user(code).password("right-password"))
                .andExpect(redirectedUrl("/login?locked"))
                .andExpect(unauthenticated());
        mvc.perform(get("/login").param("locked", ""))
                .andExpect(content().string(containsString("Too many failed sign-ins")));
    }

    @Test
    void aFewWrongPasswordsDoNotBlockTheRightOne() throws Exception {
        String code = "OK-" + UUID.randomUUID().toString().substring(0, 8);
        AppUser worker = new AppUser();
        worker.setRole(roles.findByRoleName("WORKER").orElseThrow());
        worker.setEmployeeCode(code);
        worker.setFullName("Lock Test");
        worker.setPasswordHash(passwordEncoder.encode("right-password"));
        users.saveAndFlush(worker);

        for (int i = 0; i < 4; i++) {
            mvc.perform(formLogin().userParameter("employeeCode").user(code).password("wrong"));
        }

        mvc.perform(formLogin().userParameter("employeeCode").user(code).password("right-password"))
                .andExpect(authenticated());
    }
}
