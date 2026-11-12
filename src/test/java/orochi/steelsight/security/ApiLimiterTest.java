package orochi.steelsight.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLimiterTest {

    private long now = 600_000;
    private final ApiLimiter limiter = new ApiLimiter(() -> now);

    @Test
    void refusesCallsOverTheLimitUntilTheNextMinute() {
        for (int i = 0; i < ApiLimiter.CALLS_PER_MINUTE; i++) {
            assertThat(limiter.allowCall()).isTrue();
        }
        assertThat(limiter.allowCall()).isFalse();

        now += 60_000;
        assertThat(limiter.allowCall()).isTrue();
    }

    @Test
    void oneAddressesWrongKeysDoNotLimitAnother() {
        for (int i = 0; i < ApiLimiter.WRONG_KEYS_PER_MINUTE; i++) {
            assertThat(limiter.allowWrongKey("1.1.1.1")).isTrue();
        }
        assertThat(limiter.allowWrongKey("1.1.1.1")).isFalse();
        assertThat(limiter.allowWrongKey("2.2.2.2")).isTrue();
    }

    @Test
    void wrongKeysDoNotUseUpTheRealCallersAllowance() {
        for (int i = 0; i <= ApiLimiter.WRONG_KEYS_PER_MINUTE; i++) {
            limiter.allowWrongKey("1.1.1.1");
        }
        assertThat(limiter.allowCall()).isTrue();
    }
}
