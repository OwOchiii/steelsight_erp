package orochi.steelsight.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignInLimiterTest {

    private final SignInLimiter limiter = new SignInLimiter();

    private void fail(String code, String address, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(code, address);
        }
    }

    @Test
    void locksAClientAfterTooManyFailures() {
        fail("WRK01", "1.1.1.1", SignInLimiter.FAILURES_PER_CLIENT - 1);
        assertThat(limiter.isLocked("WRK01", "1.1.1.1")).isFalse();

        fail("WRK01", "1.1.1.1", 1);
        assertThat(limiter.isLocked("WRK01", "1.1.1.1")).isTrue();
    }

    @Test
    void oneClientsFailuresDoNotLockAnotherClientOrCode() {
        fail("WRK01", "1.1.1.1", SignInLimiter.FAILURES_PER_CLIENT);

        assertThat(limiter.isLocked("WRK01", "2.2.2.2")).isFalse();
        assertThat(limiter.isLocked("WRK02", "1.1.1.1")).isFalse();
    }

    @Test
    void fakingADifferentAddressEachTimeStillHitsTheLimitForTheCode() {
        for (int i = 0; i < SignInLimiter.FAILURES_PER_CODE; i++) {
            limiter.recordFailure("WRK01", "10.0.0." + i);
        }

        assertThat(limiter.isLocked("WRK01", "9.9.9.9")).isTrue();
    }

    @Test
    void differentSpellingOfACodeCountsAsTheSameCode() {
        fail("wrk01 ", "1.1.1.1", SignInLimiter.FAILURES_PER_CLIENT);

        assertThat(limiter.isLocked("WRK01", "1.1.1.1")).isTrue();
    }

    @Test
    void successClearsOnlyThatClientsCount() {
        fail("WRK01", "1.1.1.1", SignInLimiter.FAILURES_PER_CLIENT - 1);
        limiter.recordSuccess("WRK01", "1.1.1.1");
        fail("WRK01", "1.1.1.1", SignInLimiter.FAILURES_PER_CLIENT - 1);
        assertThat(limiter.isLocked("WRK01", "1.1.1.1")).isFalse();

        for (int i = 0; i < SignInLimiter.FAILURES_PER_CODE; i++) {
            limiter.recordFailure("WRK01", "10.0.0." + i);
        }
        limiter.recordSuccess("WRK01", "1.1.1.1");
        assertThat(limiter.isLocked("WRK01", "1.1.1.1")).isTrue();
    }

    @Test
    void handlesAMissingOrVeryLongCode() {
        fail(null, "1.1.1.1", SignInLimiter.FAILURES_PER_CLIENT);
        assertThat(limiter.isLocked(null, "1.1.1.1")).isTrue();

        String longCode = "X".repeat(5000);
        fail(longCode, "1.1.1.1", SignInLimiter.FAILURES_PER_CLIENT);
        assertThat(limiter.isLocked(longCode, "1.1.1.1")).isTrue();
    }
}
