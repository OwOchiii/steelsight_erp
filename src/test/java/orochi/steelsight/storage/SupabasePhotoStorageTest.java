package orochi.steelsight.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabasePhotoStorageTest {

    private static final String BASE = "https://example.supabase.co/storage/v1/object/ticket-photos/";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    private MockRestServiceServer server;
    private SupabasePhotoStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new SupabasePhotoStorage(
                SupabasePhotoStorage.configured(builder, "https://example.supabase.co/", "secret-key"), "ticket-photos");
    }

    @Test
    void uploadsThePhotoWithARandomNameAndTheServerKey() {
        server.expect(requestTo(matchesPattern(BASE + "[0-9a-f-]{36}\\.png")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "secret-key"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andRespond(withSuccess());

        String stored = storage.store(new MockMultipartFile("photo", "evil.exe", "text/html", PNG));

        assertThat(stored).endsWith(".png");
        server.verify();
    }

    @Test
    void noPhotoMeansNoUpload() {
        assertThat(storage.store(null)).isNull();
        assertThat(storage.store(new MockMultipartFile("photo", new byte[0]))).isNull();
        server.verify();
    }

    @Test
    void refusesAFileThatIsNotAnImageWithoutUploadingIt() {
        server.expect(never(), requestTo(matchesPattern(".*")));

        assertThatThrownBy(() -> storage.store(new MockMultipartFile("photo", "a.png", "image/png", "<html>".getBytes())))
                .isInstanceOf(BusinessRuleException.class);
        server.verify();
    }

    @Test
    void aFailedUploadIsReportedRatherThanLosingThePhotoSilently() {
        server.expect(requestTo(matchesPattern(BASE + ".*"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> storage.store(new MockMultipartFile("photo", PNG)))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void loadsAStoredPhoto() throws Exception {
        server.expect(requestTo(BASE + "a.png")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        Resource photo = storage.load("a.png");

        assertThat(photo.getContentAsByteArray()).isEqualTo(PNG);
    }

    @Test
    void aMissingPhotoIsNotFoundWhicheverStatusSupabaseUses() {
        server.expect(requestTo(BASE + "gone.png")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> storage.load("gone.png")).isInstanceOf(NotFoundException.class);

        server.reset();
        server.expect(requestTo(BASE + "gone.png")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> storage.load("gone.png")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anOutageIsNotReportedAsAMissingPhoto() {
        server.expect(requestTo(BASE + "a.png")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> storage.load("a.png")).isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void aNameCannotEscapeTheBucketPath() {
        server.expect(requestTo(BASE + "..%2F..%2Fsecret")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> storage.load("../../secret")).isInstanceOf(NotFoundException.class);
        server.verify();
    }

    @Test
    void aFailedCleanupDoesNotHideTheOriginalError() {
        server.expect(requestTo(BASE + "a.png")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> storage.delete("a.png")).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
        server.verify();
    }
}
