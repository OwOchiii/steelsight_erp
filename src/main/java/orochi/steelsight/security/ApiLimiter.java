package orochi.steelsight.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Caps calls to /api/ per minute. Counts are kept in memory, so a restart clears them.
 *
 * Wrong keys are counted per address and calls with the right key across the whole API, because there is
 * only one rightful caller. The two counts are separate so that wrong keys can't use up the real caller's
 * allowance: a client can fake its address with X-Forwarded-For, even as the real caller's address.
 */
@Component
class ApiLimiter {

    static final int CALLS_PER_MINUTE = 60;
    static final int WRONG_KEYS_PER_MINUTE = 10;
    static final int RETRY_AFTER_SECONDS = 60;
    private static final long MINUTE_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final Duration KEPT_FOR = Duration.ofMinutes(2);
    private static final int LONGEST_ADDRESS_KEPT = 64;

    private final Cache<Long, Integer> calls = Caffeine.newBuilder().expireAfterWrite(KEPT_FOR).build();
    private final Cache<String, Integer> wrongKeys = Caffeine.newBuilder()
            .expireAfterWrite(KEPT_FOR)
            .maximumSize(100_000)   // The address can be faked, so memory use needs a ceiling.
            .build();
    private final LongSupplier clockMillis;

    ApiLimiter() {
        this(System::currentTimeMillis);
    }

    ApiLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** Counts a call that had the right key. */
    boolean allowCall() {
        return calls.asMap().merge(minute(), 1, Integer::sum) <= CALLS_PER_MINUTE;
    }

    /** Counts a call with a wrong or missing key. False once this address has had too many. */
    boolean allowWrongKey(String address) {
        String kept = address == null ? "" : address.substring(0, Math.min(address.length(), LONGEST_ADDRESS_KEPT));
        return wrongKeys.asMap().merge(minute() + "|" + kept, 1, Integer::sum) <= WRONG_KEYS_PER_MINUTE;
    }

    private long minute() {
        return clockMillis.getAsLong() / MINUTE_MILLIS;
    }
}
