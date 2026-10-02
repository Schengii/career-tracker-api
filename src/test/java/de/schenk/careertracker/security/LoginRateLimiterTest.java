package de.schenk.careertracker.security;

import de.schenk.careertracker.service.TooManyLoginAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    /** Clock that only moves when the test says so. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void allowsAttemptsBelowTheLimit() {
        limiter.recordFailure("k");
        limiter.recordFailure("k");

        assertThatCode(() -> limiter.checkAllowed("k")).doesNotThrowAnyException();
    }

    @Test
    void blocksAfterMaxFailuresAndReportsRetryAfter() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("k");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("k"))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(599L, 601L));
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("attacker");
        }

        assertThatCode(() -> limiter.checkAllowed("someone-else")).doesNotThrowAnyException();
    }

    @Test
    void windowExpires() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("k");
        }
        clock.advance(Duration.ofMinutes(11));

        assertThatCode(() -> limiter.checkAllowed("k")).doesNotThrowAnyException();
    }

    @Test
    void resetClearsFailures() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("k");
        }
        limiter.reset("k");

        assertThatCode(() -> limiter.checkAllowed("k")).doesNotThrowAnyException();
    }
}
