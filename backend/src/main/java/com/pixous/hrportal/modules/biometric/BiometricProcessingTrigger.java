package com.pixous.hrportal.modules.biometric;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * When stored punches become attendance.
 *
 * <p>Two triggers, and both are needed.
 *
 * <p>The webhook calls {@link #processSoon()} the moment a delivery is stored,
 * because the whole point is that presenting a face at the terminal updates the
 * screen in the office — a punch that only appeared on the next scheduled sweep
 * would be real-time in name only.
 *
 * <p>The sweep exists anyway, for everything the immediate call cannot cover: a
 * punch that arrived before its employee was mapped and could not be
 * attributed, one whose processing threw, and the backlog a device replays
 * after being offline. Without it those wait for the next punch to push them
 * along, which on a quiet afternoon is hours.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiometricProcessingTrigger {

    private final BiometricAttendanceProcessor processor;

    /**
     * Runs the pending queue off the request thread.
     *
     * <p>{@code @Async} so the webhook can answer inside Hikvision's five-second
     * timeout. Doing this work inline would be the difference between answering
     * in milliseconds and answering after however long a batch of punches takes
     * to apply — and a timeout means a redelivery of punches already stored.
     *
     * <p>Failures are swallowed here on purpose. Nothing is waiting on the
     * result, an exception escaping an async method reaches only the executor's
     * default handler, and the sweep below will try the same events again.
     */
    @Async
    public void processSoon() {
        try {
            processor.processPending();
        } catch (Exception e) {
            log.error("Biometric attendance processing failed: {}", e.getMessage());
            log.debug("Biometric attendance processing failure detail", e);
        }
    }

    /**
     * Every two minutes.
     *
     * <p>Frequent enough that a punch stranded by a missing mapping is picked
     * up within a couple of minutes of the mapping arriving, and rare enough
     * that an empty queue — which is the usual state, since the webhook has
     * already done the work — costs one indexed query.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void sweep() {
        try {
            processor.processPending();
        } catch (Exception e) {
            log.error("Scheduled biometric attendance sweep failed: {}", e.getMessage());
            log.debug("Scheduled biometric attendance sweep failure detail", e);
        }
    }
}
