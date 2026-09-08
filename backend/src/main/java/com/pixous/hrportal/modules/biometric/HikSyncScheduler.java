package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.biometric.hik.HikClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * When the Hikvision mapping is rebuilt.
 *
 * <p>Every one of these swallows its own failure. A sync that throws out of a
 * scheduled method stops nothing else, but it does fill the log with a stack
 * trace and no context, and a network blip at four in the morning is not worth
 * that — the next run repairs whatever this one missed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HikSyncScheduler {

    private final HikClient client;
    private final HikPersonSyncService syncService;

    /**
     * Once at startup, shortly after the application is ready.
     *
     * <p>A deploy is exactly when the mapping is most likely to be stale — the
     * process has been down, and people were added to the terminal while it
     * was. Waiting until the small hours would leave a working day's punches
     * unattributed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!client.isEnabled()) {
            log.info("Hikvision biometric attendance is off "
                    + "(set HIKVISION_ENABLED and supply the app key and secret to turn it on)");
            return;
        }
        safely("startup", syncService::sync);
    }

    /**
     * Hourly, at ten past.
     *
     * <p>An employee added to the terminal this morning can punch this
     * afternoon. Hourly costs a handful of requests against a budget of five a
     * second, and the alternative — waiting for the nightly run — means a new
     * joiner's first day is recorded as a string of unmatched punches.
     *
     * <p>Ten past rather than on the hour, to sit clear of everything else that
     * runs on the hour.
     */
    @Scheduled(cron = "0 10 * * * *")
    public void hourly() {
        if (!client.isEnabled()) {
            return;
        }
        safely("hourly", syncService::sync);
    }

    /**
     * The enrolment flags, nightly at 03:20.
     *
     * <p>Separate and much less frequent because it costs one request per
     * person: a hundred employees is half a minute of calls at the documented
     * rate. Whether somebody's face is enrolled changes when HR enrols it, not
     * hourly, so a nightly answer is current enough for the screen that shows
     * it.
     */
    @Scheduled(cron = "0 20 3 * * *")
    public void nightlyEnrolment() {
        if (!client.isEnabled()) {
            return;
        }
        safely("nightly enrolment", syncService::refreshEnrolment);
    }

    private void safely(String what, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            // Deliberately not rethrown: see the class note.
            log.error("Hikvision {} sync failed: {}", what, e.getMessage());
            log.debug("Hikvision {} sync failure detail", what, e);
        }
    }
}
