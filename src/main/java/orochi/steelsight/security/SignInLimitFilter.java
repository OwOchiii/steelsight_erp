package orochi.steelsight.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Turns away a locked sign-in before its password is checked, even when the password is right. */
class SignInLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SignInLimitFilter.class);

    private final SignInLimiter limiter;

    SignInLimitFilter(SignInLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !(request.getContextPath() + "/login").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String code = request.getParameter("employeeCode");
        if (limiter.isLocked(code, request.getRemoteAddr())) {
            log.warn("Sign-in locked for {}", code == null ? "" : code.replaceAll("[\\r\\n\\t]", "_"));
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }
}
