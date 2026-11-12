package orochi.steelsight.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {

    private static final String ADDRESS = "1.1.1.1";

    private final ApiKeyFilter filter = new ApiKeyFilter("right", new ApiLimiter(() -> 600_000));

    private MockHttpServletResponse call(String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/detection/alerts");
        request.setRemoteAddr(ADDRESS);
        request.addHeader(ApiKeyFilter.HEADER, key);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void tooManyWrongKeysAreToldToWait() throws Exception {
        for (int i = 0; i < ApiLimiter.WRONG_KEYS_PER_MINUTE; i++) {
            assertThat(call("wrong").getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse limited = call("wrong");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void aFloodOfWrongKeysFromTheSameAddressDoesNotBlockTheRightKey() throws Exception {
        for (int i = 0; i <= ApiLimiter.WRONG_KEYS_PER_MINUTE; i++) {
            call("wrong");
        }

        assertThat(call("right").getStatus()).isEqualTo(200);
    }

    @Test
    void tooManyCallsWithTheRightKeyAreToldToWait() throws Exception {
        for (int i = 0; i < ApiLimiter.CALLS_PER_MINUTE; i++) {
            assertThat(call("right").getStatus()).isEqualTo(200);
        }

        assertThat(call("right").getStatus()).isEqualTo(429);
    }
}
