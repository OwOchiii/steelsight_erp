package orochi.steelsight.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;

import java.io.IOException;

/**
 * After sign-in, goes home, or to the change-password page when an admin chose the password.
 *
 * It also refuses a sign-in whose password changed while it was being checked. Spring checks the
 * password, then registers the session, then calls this handler. A password change saves the new
 * hash, then ends the person's registered sessions (UserSessions). A change that lands between
 * the check and the registration would miss this session, so the hash is read again here, after
 * registration: a change saved before this read is caught below, and one saved after it finds
 * the session already registered and ends it.
 */
class FirstSignInHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AppUserRepository users;

    FirstSignInHandler(AppUserRepository users) {
        super("/");
        setAlwaysUseDefaultTargetUrl(true);
        this.users = users;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser me)) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        AppUser current = users.findById(me.getUserId()).orElse(null);
        if (current == null || !me.getPassword().equals(current.getPasswordHash())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            getRedirectStrategy().sendRedirect(request, response, "/login?error");
            return;
        }
        if (current.isMustChangePassword()) {
            request.getSession().setAttribute(PasswordChangeRequiredFilter.SESSION_FLAG, Boolean.TRUE);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, PasswordChangeRequiredFilter.PAGE);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
