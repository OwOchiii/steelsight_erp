package orochi.steelsight.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs one line per request: who asked for what, the status and how long it took. Every log line
 * written during the request carries the same id, so one request can be followed through the log.
 * Only the path is logged, because query strings and form bodies can hold personal data.
 */
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        MDC.put(REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));
        log.debug("{} {} started", request.getMethod(), request.getRequestURI());
        try {
            chain.doFilter(request, response);
            int status = response.getStatus();
            if (status >= 500) {
                log.error("{} {} -> {} user={} {}ms", request.getMethod(), request.getRequestURI(), status,
                        currentUser(), millisSince(started));
            } else {
                log.info("{} {} -> {} user={} {}ms", request.getMethod(), request.getRequestURI(), status,
                        currentUser(), millisSince(started));
            }
        } catch (ServletException | IOException | RuntimeException e) {
            // Failures past the exception handler, such as a broken template. Tomcat logs the stack trace next.
            log.error("{} {} failed user={} {}ms: {}", request.getMethod(), request.getRequestURI(),
                    currentUser(), millisSince(started), e.toString());
            throw e;
        } finally {
            MDC.remove(REQUEST_ID);
        }
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? "anonymous" : authentication.getName();
    }

    private long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
