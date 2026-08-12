package com.pixous.hrportal.security;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down repeated failed logins.
 *
 * <p>Before this, {@code /api/auth/login} accepted guesses as fast as they
 * could be sent: nothing delayed, nothing locked, nothing recorded. That
 * matters more here than in most applications, because {@code password_vault}
 * holds recoverable passwords — one guessed administrator account would expose
 * every password in the company rather than just that one.
 *
 * <p>Counting is per IP address <em>and</em> per username, and either reaching
 * the limit is enough to refuse. Per-username alone lets someone spray one
 * guess across every account from one machine; per-IP alone lets a distributed
 * attempt work through a single account.
 *
 * <p>Deliberately in memory rather than in the database. A limiter that writes
 * a row per attempt would spend the hosting account's twenty connections during
 * exactly the flood it exists to survive. The cost is that the count resets
 * when the application restarts, and that a future second instance would count
 * separately — acceptable for a brute-force speed bump, and worth revisiting if
 * this ever runs more than one instance.
 *
 * <p>Nothing about a successful login changes: the counter for that identity is
 * cleared and the request proceeds as before.
 */
@Component
public class LoginAttemptLimiter {

    /** Failures allowed within {@link #WINDOW} before refusing. */
    private static final int MAX_FAILURES = 8;

    /** How long failures are remembered, and how long a block lasts. */
    private static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * Upper bound on tracked identities.
     *
     * <p>Without it, an attacker rotating usernames or spoofed addresses would
     * grow this map until the process ran out of memory — the limiter becoming
     * the outage it was added to prevent.
     */
    private static final int MAX_TRACKED = 10_000;

    private record Attempts(int failures, Instant first) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /**
     * Refuses the request when this identity has failed too often recently.
     *
     * @param ip       caller address, as the controller already resolves it
     * @param username what they are trying to sign in as
     */
    public void checkAllowed(String ip, String username) {
        Instant now = Instant.now();
        if (isBlocked(key("ip", ip), now) || isBlocked(key("user", username), now)) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many sign-in attempts. Try again in a few minutes.");
        }
    }

    /** Records a failure against both the address and the username. */
    public void recordFailure(String ip, String username) {
        Instant now = Instant.now();
        evictIfCrowded(now);
        bump(key("ip", ip), now);
        bump(key("user", username), now);
    }

    /** Clears the count for this identity — a correct password ends the streak. */
    public void recordSuccess(String ip, String username) {
        attempts.remove(key("ip", ip));
        attempts.remove(key("user", username));
    }

    private boolean isBlocked(String key, Instant now) {
        Attempts a = attempts.get(key);
        if (a == null) return false;
        if (expired(a, now)) {
            attempts.remove(key);
            return false;
        }
        return a.failures() >= MAX_FAILURES;
    }

    private void bump(String key, Instant now) {
        attempts.compute(key, (k, existing) ->
                (existing == null || expired(existing, now))
                        ? new Attempts(1, now)
                        : new Attempts(existing.failures() + 1, existing.first()));
    }

    private boolean expired(Attempts a, Instant now) {
        return a.first().plus(WINDOW).isBefore(now);
    }

    /**
     * Drops entries whose window has passed, but only once the map is large
     * enough to be worth the sweep.
     */
    private void evictIfCrowded(Instant now) {
        if (attempts.size() < MAX_TRACKED) return;
        attempts.values().removeIf(a -> expired(a, now));
        // Still full after clearing what expired: the flood is live. Start over
        // rather than grow without limit. This forgives attempts in progress,
        // which is the lesser harm compared with exhausting memory.
        if (attempts.size() >= MAX_TRACKED) attempts.clear();
    }

    private String key(String kind, String value) {
        // Usernames are matched case-insensitively at sign-in, so the counter
        // has to be too, or alternating capitalisation would reset it.
        return kind + ':' + (value == null ? "?" : value.trim().toLowerCase());
    }
}
