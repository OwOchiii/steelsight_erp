package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@Transactional
class AccountPagesTest extends PageTestSupport {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    Long admin;
    Long manager;
    Long worker;
    Long team;

    @BeforeEach
    void seed() {
        admin = userWithRole("ACC-A", "Ada Admin", "ADMIN");
        manager = userWithRole("ACC-M", "Mo Manager", "MANAGER");
        worker = userWithRole("ACC-W", "Wanda Worker", "WORKER");
        team = team("Acc Team", manager);
        joinTeam(worker, team);
    }

    private Long idOf(String code) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE employee_code = ?", Long.class, code);
    }

    @Test
    void adminCreatesAnAccountThatMustChangeItsPassword() throws Exception {
        mvc.perform(post("/team/new").param("employeeCode", "ACC-NEW").param("fullName", "New Person")
                        .param("roleName", "WORKER").param("teamId", team.toString()).param("password", "start-pass-1")
                        .with(as(admin)).with(csrf()))
                .andExpect(header().string("Location", containsString("/edit")))
                .andExpect(flash().attribute("success", "The account was created."));

        Long created = idOf("ACC-NEW");
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM app_user WHERE id = ?", Boolean.class, created)).isTrue();
        assertThat(jdbc.queryForObject("SELECT team_id FROM app_user WHERE id = ?", Long.class, created)).isEqualTo(team);
        mvc.perform(get("/team/" + created + "/edit").with(as(admin)))
                .andExpect(content().string(containsString("No face photo")));
    }

    @Test
    void adminCreatesAnAccountWithAFacePhotoThatIsQueuedForEnrolment() throws Exception {
        mvc.perform(multipart("/team/new").file(new MockMultipartFile("photo", "face.jpg", "image/jpeg", JPEG))
                        .param("employeeCode", "ACC-FACE").param("fullName", "Fay Face").param("roleName", "WORKER")
                        .param("password", "start-pass-1").with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Long created = idOf("ACC-FACE");
        assertThat(textColumn("SELECT status FROM face_embedding WHERE user_id = ?", created)).isEqualTo("PENDING");
        mvc.perform(get("/team/" + created + "/edit").with(as(admin)))
                .andExpect(content().string(containsString("Waiting for enrolment")))
                .andExpect(content().string(containsString("/team/" + created + "/face")));
        mvc.perform(get("/team/" + created + "/face").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/team").with(as(admin)))
                .andExpect(content().string(containsString("Waiting for enrolment")));
    }

    @Test
    void aBadNewAccountIsShownAgainWithoutThePassword() throws Exception {
        mvc.perform(post("/team/new").param("employeeCode", "has space").param("fullName", "Typed Name")
                        .param("roleName", "WORKER").param("password", "start-pass-1").with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("An employee code can use letters")))
                .andExpect(content().string(containsString("Typed Name")))
                .andExpect(content().string(not(containsString("start-pass-1"))));
    }

    @Test
    void adminChangesRoleTeamStatusPasswordAndPhoto() throws Exception {
        mvc.perform(post("/team/" + worker + "/role").param("roleName", "HSE_OFFICER").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The role was changed."));
        mvc.perform(post("/team/" + worker + "/team").param("teamId", "").with(as(admin)).with(csrf()));
        mvc.perform(post("/team/" + worker + "/deactivate").with(as(admin)).with(csrf()));
        flush();
        assertThat(jdbc.queryForObject("SELECT is_active FROM app_user WHERE id = ?", Boolean.class, worker)).isFalse();
        mvc.perform(post("/team/" + worker + "/reactivate").with(as(admin)).with(csrf()));
        mvc.perform(post("/team/" + worker + "/password").param("newPassword", "temp-pass-1").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The password was reset. They must change it at their next sign-in."));
        mvc.perform(multipart("/team/" + worker + "/face").file(new MockMultipartFile("photo", "f.jpg", "image/jpeg", JPEG))
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("success", "The face photo was saved and queued for enrolment."));
        flush();

        assertThat(textColumn("SELECT r.role_name FROM app_user u JOIN role r ON r.id = u.role_id WHERE u.id = ?", worker)).isEqualTo("HSE_OFFICER");
        assertThat(jdbc.queryForObject("SELECT team_id FROM app_user WHERE id = ?", Long.class, worker)).isNull();
        assertThat(jdbc.queryForObject("SELECT is_active AND must_change_password FROM app_user WHERE id = ?", Boolean.class, worker)).isTrue();
        assertThat(textColumn("SELECT face_file_path FROM app_user WHERE id = ?", worker)).endsWith(".jpg");
    }

    @Test
    void uploadingNothingOrAFakeImageIsRefused() throws Exception {
        mvc.perform(multipart("/team/" + worker + "/face").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "Choose a photo to upload"));
        mvc.perform(multipart("/team/" + worker + "/face").file(new MockMultipartFile("photo", "f.jpg", "image/jpeg", "<html>".getBytes()))
                        .with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "The photo must be a JPEG, PNG or WebP image"));
        assertThat(textColumn("SELECT face_file_path FROM app_user WHERE id = ?", worker)).isNull();
    }

    @Test
    void deletingNeedsTheBoxTicked() throws Exception {
        mvc.perform(post("/team/" + worker + "/delete").with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/team/" + worker + "/edit"))
                .andExpect(flash().attribute("error", "Tick the box to confirm the deletion"));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM app_user WHERE id = ?", Boolean.class, worker)).isTrue();

        mvc.perform(post("/team/" + worker + "/delete").param("confirm", "true").with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/team"))
                .andExpect(flash().attribute("success", "The account was deleted."));
        flush();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM app_user WHERE id = ?", Boolean.class, worker)).isTrue();
    }

    @Test
    void anAdminsOwnAccountHidesTheActionsThatWouldLockThemOut() throws Exception {
        mvc.perform(get("/team/" + admin + "/edit").with(as(admin)))
                .andExpect(content().string(containsString("This is your account")))
                .andExpect(content().string(not(containsString("/team/" + admin + "/role"))))
                .andExpect(content().string(not(containsString("/team/" + admin + "/delete"))));
        mvc.perform(post("/team/" + admin + "/delete").param("confirm", "true").with(as(admin)).with(csrf()))
                .andExpect(flash().attribute("error", "You cannot delete your own account"));
    }

    @Test
    void managersAndWorkersCannotManageAccounts() throws Exception {
        mvc.perform(get("/team").with(as(manager)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/team/new"))))
                .andExpect(content().string(not(containsString("No face photo"))));
        for (Long person : new Long[]{manager, worker}) {
            mvc.perform(get("/team/new").with(as(person))).andExpect(status().isForbidden());
            mvc.perform(post("/team/new").param("employeeCode", "ACC-X").param("fullName", "X").param("roleName", "ADMIN")
                    .param("password", "start-pass-1").with(as(person)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(get("/team/" + worker + "/edit").with(as(person))).andExpect(status().isForbidden());
            mvc.perform(get("/team/" + worker + "/face").with(as(person))).andExpect(status().isForbidden());
            mvc.perform(multipart("/team/" + worker + "/face").file(new MockMultipartFile("photo", "f.jpg", "image/jpeg", JPEG))
                    .with(as(person)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/team/" + worker + "/role").param("roleName", "ADMIN").with(as(person)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/team/" + worker + "/password").param("newPassword", "hacked-pass").with(as(person)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/team/" + worker + "/delete").param("confirm", "true").with(as(person)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE employee_code = 'ACC-X'", Integer.class)).isZero();
        assertThat(textColumn("SELECT r.role_name FROM app_user u JOIN role r ON r.id = u.role_id WHERE u.id = ?", worker)).isEqualTo("WORKER");
    }

    @Test
    void aManagerStillPlansShiftsAndSetsTheLateRule() throws Exception {
        mvc.perform(get("/shifts/new").param("workerId", worker.toString()).with(as(manager))).andExpect(status().isOk());
        mvc.perform(post("/teams/" + team + "/late-after").param("minutes", "20").with(as(manager)).with(csrf()))
                .andExpect(flash().attribute("success", "The late check-in rule was saved."));
    }

    @Test
    void someoneGivenAPasswordByAnAdminMustChooseTheirOwnFirst() throws Exception {
        String code = "ACC-" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/team/new").param("employeeCode", code).param("fullName", "First Timer")
                .param("roleName", "WORKER").param("password", "start-pass-1").with(as(admin)).with(csrf()));

        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin().userParameter("employeeCode").user(code).password("start-pass-1"))
                .andExpect(redirectedUrl("/account/password"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/tickets").session(session)).andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/account/password").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("An admin set your password")))
                .andExpect(content().string(not(containsString("class=\"sidebar\""))));
        mvc.perform(post("/account/password").session(session).with(csrf()).param("currentPassword", "start-pass-1")
                        .param("newPassword", "my-own-pass").param("confirmPassword", "my-own-typo"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("don&#39;t match")));
        mvc.perform(post("/account/password").session(session).with(csrf()).param("currentPassword", "start-pass-1")
                        .param("newPassword", "my-own-pass").param("confirmPassword", "my-own-pass"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        mvc.perform(formLogin().userParameter("employeeCode").user(code).password("start-pass-1"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin().userParameter("employeeCode").user(code).password("my-own-pass"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void everyoneCanChangeTheirOwnPasswordFromTheMenu() throws Exception {
        mvc.perform(get("/").with(as(worker)))
                .andExpect(content().string(containsString("href=\"/account/password\"")));
        mvc.perform(get("/account/password").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("An admin set your password"))))
                .andExpect(content().string(containsString("class=\"sidebar\"")));
    }
}
