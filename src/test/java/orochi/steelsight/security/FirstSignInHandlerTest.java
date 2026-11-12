package orochi.steelsight.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.Role;
import orochi.steelsight.identity.repository.AppUserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The handler runs after the session is registered. Here the database already holds a new hash
 * while the sign-in was checked against the old one: the password changed mid-sign-in.
 */
class FirstSignInHandlerTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final FirstSignInHandler handler = new FirstSignInHandler(users);

    @Test
    void aPasswordChangedDuringSignInRefusesTheSession() throws Exception {
        AuthenticatedUser signingIn = new AuthenticatedUser(user("old-hash", false));
        when(users.findById(7L)).thenReturn(Optional.of(user("new-hash", false)));
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = signIn(signingIn, session);

        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
    }

    @Test
    void anUnchangedPasswordSignsIn() throws Exception {
        AuthenticatedUser signingIn = new AuthenticatedUser(user("same-hash", false));
        when(users.findById(7L)).thenReturn(Optional.of(user("same-hash", false)));
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = signIn(signingIn, session);

        assertThat(session.isInvalid()).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void anAdminChosenPasswordStillGoesToTheChangePasswordPage() throws Exception {
        AuthenticatedUser signingIn = new AuthenticatedUser(user("same-hash", true));
        when(users.findById(7L)).thenReturn(Optional.of(user("same-hash", true)));
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = signIn(signingIn, session);

        assertThat(response.getRedirectedUrl()).isEqualTo(PasswordChangeRequiredFilter.PAGE);
        assertThat(session.getAttribute(PasswordChangeRequiredFilter.SESSION_FLAG)).isEqualTo(Boolean.TRUE);
    }

    private MockHttpServletResponse signIn(AuthenticatedUser principal, MockHttpSession session) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response,
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        return response;
    }

    private static AppUser user(String hash, boolean mustChange) {
        Role role = new Role();
        role.setRoleName("WORKER");
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmployeeCode("W-007");
        user.setFullName("Test Worker");
        user.setPasswordHash(hash);
        user.setRole(role);
        user.setActive(true);
        user.setMustChangePassword(mustChange);
        return user;
    }
}
