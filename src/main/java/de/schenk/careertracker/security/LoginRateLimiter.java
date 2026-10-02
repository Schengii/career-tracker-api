package de.schenk.careertracker.security;

import de.schenk.careertracker.service.TooManyLoginAttemptsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter for failed logins, keyed by client IP + username.
 * State lives in memory, so it protects a single instance; a multi-instance deployment
 * would need a shared store (e.g. Redis) or a gateway-level limit.
 */
@Component
public class LoginRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimiter(@Value("${app.login.max-attempts:5}") int maxAttempts,
                            @Value("${app.login.window:PT15M}") Duration window) {
        this(maxAttempts, window, Clock.systemUTC());
    }

    public LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Throws if the key already used up its failed attempts within the window. */
    public void checkAllowed(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() >= maxAttempts) {
                long retryAfter = Duration.between(now, attempts.peekFirst().plus(window)).toSeconds() + 1;
                throw new TooManyLoginAttemptsException(Math.max(retryAfter, 1));
            }
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant now = clock.instant();
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }

    private void cleanup() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }
}
