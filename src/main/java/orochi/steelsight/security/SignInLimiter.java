package orochi.steelsight.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Slows password guessing, because employee codes are easy to guess. Counts are kept in memory, so a
 * restart clears them.
 *
 * A client can fake its address with X-Forwarded-For, which would dodge a limit based on address alone,
 * so each employee code also has a higher limit across all addresses. The lower per-address limit means
 * a stranger needs many more failures to lock someone else out than to lock themselves.
 */
@Component
class SignInLimiter {

    static final int FAILURES_PER_CLIENT = 5;
    static final int FAILURES_PER_CODE = 20;
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final int LONGEST_CODE_KEPT = 64;

    private final Cache<String, Integer> failures = Caffeine.newBuilder()
            .expireAfterWrite(LOCK)
            .maximumSize(100_000)   // The code is whatever was typed, so memory use needs a ceiling.
            .build();

    boolean isLocked(String code, String address) {
        String typed = normalise(code);
        return count(clientKey(typed, address)) >= FAILURES_PER_CLIENT
                || count(codeKey(typed)) >= FAILURES_PER_CODE;
    }

    void recordFailure(String code, String address) {
        String typed = normalise(code);
        failures.asMap().merge(clientKey(typed, address), 1, Integer::sum);
        failures.asMap().merge(codeKey(typed), 1, Integer::sum);
    }

    /** Clears only this client's count, so a stranger's guesses against the code still add up. */
    void recordSuccess(String code, String address) {
        failures.invalidate(clientKey(normalise(code), address));
    }

    @EventListener
    void failed(AbstractAuthenticationFailureEvent event) {
        recordFailure(event.getAuthentication().getName(), addressOf(event.getAuthentication()));
    }

    @EventListener
    void succeeded(AuthenticationSuccessEvent event) {
        recordSuccess(event.getAuthentication().getName(), addressOf(event.getAuthentication()));
    }

    private int count(String key) {
        Integer count = failures.getIfPresent(key);
        return count == null ? 0 : count;
    }

    private String addressOf(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress()
                : "";
    }

    private String normalise(String code) {
        String typed = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        return typed.length() > LONGEST_CODE_KEPT ? typed.substring(0, LONGEST_CODE_KEPT) : typed;
    }

    private String clientKey(String code, String address) {
        return "client|" + code + "|" + address;
    }

    private String codeKey(String code) {
        return "code|" + code;
    }
}
