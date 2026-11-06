package orochi.steelsight.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Records sign-ins. Never logs the password. */
@Component
class SignInLog {

    private static final Logger log = LoggerFactory.getLogger(SignInLog.class);

    @EventListener
    void succeeded(AuthenticationSuccessEvent event) {
        log.info("Signed in: {}", clean(event.getAuthentication().getName()));
    }

    @EventListener
    void failed(AbstractAuthenticationFailureEvent event) {
        log.warn("Sign-in refused for {}: {}", clean(event.getAuthentication().getName()),
                event.getException().getClass().getSimpleName());
    }

    /** The employee code is whatever was typed, so it must not be able to forge a log line. */
    private String clean(String typed) {
        return typed.replaceAll("[\\r\\n\\t]", "_");
    }
}
