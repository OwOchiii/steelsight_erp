package orochi.steelsight.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.IOException;

/**
 * Reads an upload before Spring Security does. When an upload is over the container's ceiling, Tomcat
 * can't read the form, so the CSRF check finds no token and refuses it as forbidden, which hides the
 * real problem. Reading it here first means the person is told the file is too large.
 * Runs just after the character encoding filter, which must come before anything reads the form.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MultipartLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MultipartLimitFilter.class);

    private final StandardServletMultipartResolver multipart = new StandardServletMultipartResolver();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (multipart.isMultipart(request)) {
            try {
                // Tomcat keeps the parsed parts, so later readers don't parse the body again.
                multipart.resolveMultipart(request);
            } catch (MaxUploadSizeExceededException e) {
                log.info("Upload over the size ceiling refused");
                refuse(request, response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e);
                return;
            } catch (MultipartException e) {
                log.info("Upload unreadable: {}", e.getMostSpecificCause().getClass().getSimpleName());
                refuse(request, response, HttpServletResponse.SC_BAD_REQUEST, e);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Hands over to the error page. Recording the failure tells the DispatcherServlet that the upload
     * can't be read, so it doesn't try again while showing the error page and fail a second time.
     */
    private static void refuse(HttpServletRequest request, HttpServletResponse response, int status,
                               MultipartException failure) throws IOException {
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, failure);
        response.sendError(status);
    }
}
