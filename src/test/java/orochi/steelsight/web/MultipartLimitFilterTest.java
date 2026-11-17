package orochi.steelsight.web;

import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartLimitFilterTest {

    private final MultipartLimitFilter filter = new MultipartLimitFilter();

    /** Tomcat's own words when a file is over max-file-size. */
    @Test
    void anUploadOverTheCeilingIsRefusedAsTooLargeBeforeTheCsrfCheckSeesIt() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(failingUpload(new IllegalStateException(
                "The field photo exceeds its maximum permitted size of 20971520 bytes.")), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).as("the rest of the chain is skipped").isNull();
    }

    @Test
    void aBrokenUploadIsABadRequest() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(failingUpload(new IllegalStateException("Stream ended unexpectedly")), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void otherRequestsPassStraightThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tickets/1/comments");
        request.setContentType("application/x-www-form-urlencoded");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest failingUpload(RuntimeException failure) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tickets") {
            @Override
            public Collection<Part> getParts() {
                throw failure;
            }
        };
        request.setContentType("multipart/form-data; boundary=x");
        return request;
    }
}
