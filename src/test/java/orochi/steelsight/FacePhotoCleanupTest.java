package orochi.steelsight;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Not transactional, so changes really commit and the after-commit cleanup really runs. */
@SpringBootTest(properties = "steelsight.storage.dir=target/test-uploads-cleanup")
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class FacePhotoCleanupTest extends PageTestSupport {

    private static final Path FACES = Path.of("target/test-uploads-cleanup/faces");

    @Test
    void anOldFacePhotoIsRemovedOnlyAfterItsReplacementOrTheDeletionCommits() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Long admin = userWithRole("CLN-A-" + unique, "Clean Admin", "ADMIN");
        String code = "CLN-W-" + unique;

        mvc.perform(multipart("/team/new").file(photo()).param("employeeCode", code).param("fullName", "Clean Worker")
                        .param("roleName", "WORKER").param("password", "start-pass-1").with(as(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Long worker = jdbc.queryForObject("SELECT id FROM app_user WHERE employee_code = ?", Long.class, code);
        String first = textColumn("SELECT face_file_path FROM app_user WHERE id = ?", worker);
        assertThat(FACES.resolve(first)).exists();

        mvc.perform(multipart("/team/" + worker + "/face").file(photo()).with(as(admin)).with(csrf()));
        String second = textColumn("SELECT face_file_path FROM app_user WHERE id = ?", worker);
        assertThat(second).isNotEqualTo(first);
        assertThat(FACES.resolve(first)).doesNotExist();
        assertThat(FACES.resolve(second)).exists();

        mvc.perform(post("/team/" + worker + "/delete").param("confirm", "true").with(as(admin)).with(csrf()));
        assertThat(FACES.resolve(second)).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM face_embedding WHERE user_id = ?", Integer.class, worker)).isZero();
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", "f.jpg", "image/jpeg", AccountPagesTest.JPEG);
    }
}
