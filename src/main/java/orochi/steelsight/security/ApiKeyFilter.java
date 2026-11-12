package orochi.steelsight.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Lets software call /api/ with a shared key. With no key configured, every call is refused. */
class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final String CALLER = "detection-software";

    private final byte[] key;
    private final ApiLimiter limiter;

    ApiKeyFilter(String key, ApiLimiter limiter) {
        this.key = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        this.limiter = limiter;
    }

    /** The key is checked before any limit, so that a flood of wrong keys can't turn away the real caller. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!matches(request.getHeader(HEADER))) {
            if (limiter.allowWrongKey(request.getRemoteAddr())) {
                log.warn("API call refused: {}", key.length == 0 ? "no API key is configured" : "wrong or missing key");
                refuse(response);
            } else {
                tooMany(response);   // Not logged: a flood must not fill the logs.
            }
            return;
        }
        if (!limiter.allowCall()) {
            log.warn("API call refused: more than {} calls this minute", ApiLimiter.CALLS_PER_MINUTE);
            tooMany(response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                CALLER, null, AuthorityUtils.NO_AUTHORITIES));
        chain.doFilter(request, response);
    }

    private boolean matches(String given) {
        return key.length > 0 && given != null && MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8));
    }

    static void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"A valid API key is required\"}");
    }

    private static void tooMany(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ApiLimiter.RETRY_AFTER_SECONDS));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Too many calls\"}");
    }
}
