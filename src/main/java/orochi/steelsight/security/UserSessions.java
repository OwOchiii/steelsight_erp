package orochi.steelsight.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/** Ends a person's sign-ins, so that someone using an old password can't stay signed in after it changes. */
@Component
@RequiredArgsConstructor
public class UserSessions {

    private final SessionRegistry registry;

    public void endAll(Long userId) {
        endExcept(userId, null);
    }

    /** For someone changing their own password, who stays signed in on the device they did it from. */
    public void endOthers(Long userId, String keptSessionId) {
        endExcept(userId, keptSessionId);
    }

    private void endExcept(Long userId, String keptSessionId) {
        registry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof AuthenticatedUser user && user.getUserId().equals(userId))
                .flatMap(principal -> registry.getAllSessions(principal, false).stream())
                .filter(session -> !session.getSessionId().equals(keptSessionId))
                .forEach(SessionInformation::expireNow);
    }
}
