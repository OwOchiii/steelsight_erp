package orochi.steelsight.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;

import java.io.IOException;

/** After sign-in, goes home, or to the change-password page when an admin chose the password. */
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
        if (authentication.getPrincipal() instanceof AuthenticatedUser me
                && users.findById(me.getUserId()).map(AppUser::isMustChangePassword).orElse(false)) {
            request.getSession().setAttribute(PasswordChangeRequiredFilter.SESSION_FLAG, Boolean.TRUE);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, PasswordChangeRequiredFilter.PAGE);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
