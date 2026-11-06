package orochi.steelsight.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Keeps someone whose password an admin chose on the change-password page until they pick their own.
 * The sign-in sets a flag on the session, so this costs no database query per request.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String SESSION_FLAG = "steelsight.mustChangePassword";
    public static final String PAGE = "/account/password";

    private static final Set<String> OPEN = Set.of(PAGE, "/logout", "/login", "/language", "/error", "/actuator/health");
    private static final List<String> OPEN_FOLDERS = List.of("/css/", "/js/", "/img/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_FLAG) != null && !isOpen(pathOf(request))) {
            response.sendRedirect(request.getContextPath() + PAGE);
            return;
        }
        chain.doFilter(request, response);
    }

    private String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private boolean isOpen(String path) {
        return OPEN.contains(path) || OPEN_FOLDERS.stream().anyMatch(path::startsWith);
    }
}
