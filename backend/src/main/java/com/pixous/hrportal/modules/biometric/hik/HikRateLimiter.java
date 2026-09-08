package com.pixous.hrportal.modules.biometric.hik;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps outbound calls under Hikvision's published ceiling.
 *
 * <p>§3.1 of the V2.15.0 guide: "No more than 5 times are allowed for request
 * per second." The document does not say what happens on the sixth, and that is
 * the reason to stay under it rather than to find out — a throttled or blocked
 * integration fails at exactly the moment it is busiest, which is a shift
 * change, which is when the punches actually matter.
 *
 * <p>A sliding window rather than a token bucket, because the rule is worded as
 * a count within a second and a bucket would permit a burst that satisfies an
 * average while breaking the literal limit.
 *
 * <p>Blocking rather than rejecting. Every caller here is a background sync or
 * a webhook already answered, so waiting a few hundred milliseconds costs
 * nothing, whereas dropping a call would leave the mapping half-built with no
 * indication which half.
 */
@Slf4j
@Component
public class HikRateLimiter {

    /**
     * One below the documented five.
     *
     * <p>The limit is enforced at Hikvision's end against its own clock, not
     * ours, and two machines disagreeing by a few milliseconds is enough to
     * turn our fifth call into their sixth. The spare slot costs a fifth of the
     * throughput on a path that is never latency-critical.
     */
    private static final int MAX_PER_SECOND = 4;

    private static final long WINDOW_MS = 1000L;

    /** Timestamps of the calls inside the current window, oldest first. */
    private final Deque<Long> recent = new ArrayDeque<>();

    /**
     * Returns once it is safe to make one call, blocking if it is not yet.
     *
     * <p>Synchronized on the limiter: the person sync and the webhook's own
     * follow-up lookups can run at once, and two threads each counting only
     * their own calls would together exceed the limit while both believed they
     * were within it.
     */
    public synchronized void acquire() {
        while (true) {
            long now = System.currentTimeMillis();
            // Drop everything that has aged out of the window.
            while (!recent.isEmpty() && now - recent.peekFirst() >= WINDOW_MS) {
                recent.pollFirst();
            }
            if (recent.size() < MAX_PER_SECOND) {
                recent.addLast(now);
                return;
            }
            // Wait exactly until the oldest call leaves the window, not a fixed
            // interval: a fixed sleep either wastes time or wakes too early and
            // spins.
            long waitMs = WINDOW_MS - (now - recent.peekFirst());
            if (waitMs <= 0) {
                continue;
            }
            try {
                wait(waitMs);
            } catch (InterruptedException e) {
                // Shutdown, or a cancelled sync. Restore the flag and let the
                // caller decide -- swallowing it would make the thread
                // uninterruptible for as long as the sync runs.
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for a Hikvision call slot", e);
            }
        }
    }
}
