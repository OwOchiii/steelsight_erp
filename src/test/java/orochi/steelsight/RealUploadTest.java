package orochi.steelsight;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sends the New Account form to a real Tomcat, as a browser does. MockMvc skips Tomcat's multipart
 * parser, so only this catches its limits: with the default part count every new account was refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "steelsight.storage.dir=target/test-uploads")
@Import(TestDatabase.class)
class RealUploadTest {

    private static final String ADMIN = "UPL-ADMIN";
    private static final String WORKER = "UPL-WORKER";
    private static final String TEAM = "Upload test team";

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).build();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM app_user WHERE employee_code IN (?, ?)", ADMIN, WORKER);
        jdbc.update("DELETE FROM team WHERE name = ?", TEAM);
    }

    @Test
    void theWholeNewAccountFormGetsThroughTomcat() throws Exception {
        jdbc.update("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash, staff_group, must_change_password)
                SELECT id, ?, 'Upload Admin', ?, 'OFFICE', false FROM role WHERE role_name = 'ADMIN'""",
                ADMIN, encoder.encode("admin-pass-1"));
        Long team = jdbc.queryForObject("INSERT INTO team (name) VALUES (?) RETURNING id", Long.class, TEAM);
        signIn();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("_csrf", csrfOn("/team/new"));
        fields.put("employeeCode", WORKER);
        fields.put("fullName", "Upload Worker");
        fields.put("roleName", "WORKER");
        fields.put("staffGroup", "SITE");
        fields.put("teamId", team.toString());
        fields.put("email", "");
        fields.put("phoneNumber", "0911000123");
        fields.put("password", "start-pass-1");
        fields.put("consent", "true");
        fields.put("_consent", "on");
        HttpResponse<String> response = postMultipart("/team/new", fields);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValueSatisfying(to -> assertThat(to).endsWith("/edit"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE employee_code = ?", Long.class, WORKER)).isEqualTo(1);
    }

    private void signIn() throws Exception {
        String form = "employeeCode=" + ADMIN + "&password=admin-pass-1&_csrf=" + URLEncoder.encode(csrfOn("/login"), StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(request("/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(302);
    }

    /** The fields, then an empty photo, as a browser sends a form whose file was left empty. */
    private HttpResponse<String> postMultipart(String path, Map<String, String> fields) throws Exception {
        String boundary = "----steelsight-test";
        StringBuilder body = new StringBuilder();
        fields.forEach((name, value) -> body.append("--").append(boundary)
                .append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n").append(value).append("\r\n"));
        body.append("--").append(boundary)
                .append("\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n")
                .append("--").append(boundary).append("--\r\n");
        return http.send(request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrfOn(String path) throws Exception {
        String page = http.send(request(path).build(), HttpResponse.BodyHandlers.ofString()).body();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"").matcher(page);
        assertThat(token.find()).as("CSRF token on " + path).isTrue();
        return token.group(1) != null ? token.group(1) : token.group(2);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }
}
