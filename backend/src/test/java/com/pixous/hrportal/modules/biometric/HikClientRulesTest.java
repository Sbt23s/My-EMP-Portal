package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.biometric.hik.HikApiException;
import com.pixous.hrportal.modules.biometric.hik.HikPerson;
import com.pixous.hrportal.modules.biometric.hik.HikRateLimiter;
import com.pixous.hrportal.modules.biometric.hik.HikTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three rules that keep the Hikvision link from breaking itself.
 *
 * <p>All three fail quietly rather than loudly, which is why they are pinned
 * here. Exceeding the request ceiling gets the integration throttled at the
 * moment it is busiest; a token refreshed too late fails a punch lookup on a
 * technicality; and retrying a wrong secret key loops forever against a
 * rate-limited API.
 */
class HikClientRulesTest {

    @Nested
    @DisplayName("Staying under the published request ceiling")
    class RateLimit {

        @Test
        @DisplayName("A burst is spread out rather than sent at once")
        void burstIsSpread() {
            /*
             * §3.1: "No more than 5 times are allowed for request per second."
             * The limiter is set one below that, so eight calls cannot fit in a
             * single second and the eighth has to wait for the window to move.
             *
             * Timed rather than counted: a counter would agree with whatever
             * the implementation does, and the thing that matters is real
             * elapsed time, because Hikvision measures it against its own
             * clock.
             */
            HikRateLimiter limiter = new HikRateLimiter();
            long start = System.currentTimeMillis();
            for (int i = 0; i < 8; i++) {
                limiter.acquire();
            }
            long elapsed = System.currentTimeMillis() - start;

            // Four go immediately, the next four wait for the first four to age
            // out -- so at least one full window has to pass.
            assertThat(elapsed)
                    .as("eight calls must not all fit inside one second")
                    .isGreaterThanOrEqualTo(900L);
        }

        @Test
        @DisplayName("A handful of calls is not slowed down")
        void smallBurstIsImmediate() {
            // The limiter must not tax the common case. Four is the budget for
            // one second and should cost nothing.
            HikRateLimiter limiter = new HikRateLimiter();
            long start = System.currentTimeMillis();
            for (int i = 0; i < 4; i++) {
                limiter.acquire();
            }
            assertThat(System.currentTimeMillis() - start).isLessThan(200L);
        }
    }

    @Nested
    @DisplayName("When a token has to be replaced")
    class TokenRefresh {

        private static final long MARGIN = 6 * 60 * 60;   // six hours

        @Test
        @DisplayName("A token with days left is kept")
        void freshTokenIsKept() {
            Instant now = Instant.parse("2026-09-08T09:00:00Z");
            HikTokenStore store = new HikTokenStore(
                    "hcc.abc", now.plusSeconds(7L * 24 * 60 * 60), "https://isgp.hikcentralconnect.com");
            assertThat(store.needsRefresh(now, MARGIN)).isFalse();
        }

        @Test
        @DisplayName("A token inside the margin is replaced before it is used")
        void nearExpiryIsRefreshed() {
            /*
             * The failure this prevents: a token with an hour left passes a
             * naive check, and then expires between that check and the call --
             * OPEN000006, on a lookup triggered by a punch that has already
             * happened. The guide explicitly supports logging in again early.
             */
            Instant now = Instant.parse("2026-09-08T09:00:00Z");
            HikTokenStore store = new HikTokenStore(
                    "hcc.abc", now.plusSeconds(60 * 60), "https://isgp.hikcentralconnect.com");
            assertThat(store.needsRefresh(now, MARGIN)).isTrue();
        }

        @Test
        @DisplayName("An expired token is replaced")
        void expiredIsRefreshed() {
            Instant now = Instant.parse("2026-09-08T09:00:00Z");
            HikTokenStore store = new HikTokenStore(
                    "hcc.abc", now.minusSeconds(1), "https://isgp.hikcentralconnect.com");
            assertThat(store.needsRefresh(now, MARGIN)).isTrue();
        }

        @Test
        @DisplayName("A missing or blank token is replaced rather than sent")
        void missingIsRefreshed() {
            // Sending an empty Token header would fail as OPEN000007 and read
            // in the log like a revoked credential rather than a bug here.
            Instant now = Instant.parse("2026-09-08T09:00:00Z");
            Instant far = now.plusSeconds(7L * 24 * 60 * 60);
            assertThat(new HikTokenStore(null, far, "d").needsRefresh(now, MARGIN)).isTrue();
            assertThat(new HikTokenStore("  ", far, "d").needsRefresh(now, MARGIN)).isTrue();
            assertThat(new HikTokenStore("hcc.abc", null, "d").needsRefresh(now, MARGIN)).isTrue();
        }
    }

    @Nested
    @DisplayName("Which failures are worth retrying")
    class Failures {

        @Test
        @DisplayName("A token problem is recoverable by logging in again")
        void tokenProblems() {
            assertThat(new HikApiException(HikApiException.TOKEN_EXPIRED, "x").isTokenProblem())
                    .isTrue();
            assertThat(new HikApiException(HikApiException.TOKEN_INVALID, "x").isTokenProblem())
                    .isTrue();
        }

        @Test
        @DisplayName("A wrong key is not retried, because retrying cannot fix it")
        void credentialProblems() {
            /*
             * The distinction the retry depends on. Treating a wrong secret key
             * as a token problem would log in, fail, log in again -- forever,
             * against an API that allows five requests a second.
             */
            HikApiException badKey = new HikApiException(HikApiException.AK_NOT_FOUND, "x");
            assertThat(badKey.isTokenProblem()).isFalse();
            assertThat(badKey.isCredentialProblem()).isTrue();

            HikApiException badSecret = new HikApiException(HikApiException.SK_INCORRECT, "x");
            assertThat(badSecret.isTokenProblem()).isFalse();
            assertThat(badSecret.isCredentialProblem()).isTrue();
        }

        @Test
        @DisplayName("Anything else is neither, and is left to the next scheduled run")
        void otherProblems() {
            // OPEN000009, a network exception at Hikvision's end. Not a token
            // problem and not a credential one -- retrying immediately would
            // just spend the budget.
            HikApiException network = new HikApiException("OPEN000009", "x");
            assertThat(network.isTokenProblem()).isFalse();
            assertThat(network.isCredentialProblem()).isFalse();
        }
    }

    @Nested
    @DisplayName("An unconfigured link does not look like an empty terminal")
    class SyncReporting {

        @Test
        @DisplayName("A skipped sync says so rather than reporting zero people")
        void skippedIsNotEmpty() {
            /*
             * The bug this pins, seen on a running server before it was fixed:
             * calling sync with no credentials answered
             *
             *   {"success":true,"message":"0 on the terminal, 0 matched ..."}
             *
             * which an administrator reads as "the terminal has nobody on it"
             * and goes to look at the hardware. Both situations produce the
             * same counts, so the counts cannot be what distinguishes them.
             */
            HikPersonSyncService.SyncResult skipped =
                    new HikPersonSyncService.SyncResult(false, 0, 0, 0, 0, 0);
            assertThat(skipped.ran()).isFalse();
            assertThat(skipped.summary()).contains("not configured");
            assertThat(skipped.summary()).doesNotContain("0 on the terminal");
        }

        @Test
        @DisplayName("A real sync that found nobody still reports the counts")
        void ranButEmpty() {
            // The other half: the terminal really is empty, and that must not
            // be reported as a configuration problem either.
            HikPersonSyncService.SyncResult empty =
                    new HikPersonSyncService.SyncResult(true, 0, 0, 0, 0, 0);
            assertThat(empty.ran()).isTrue();
            assertThat(empty.summary()).contains("0 on the terminal");
            assertThat(empty.summary()).doesNotContain("not configured");
        }

        @Test
        @DisplayName("A sync that did work reads as what it did")
        void ranWithWork() {
            HikPersonSyncService.SyncResult done =
                    new HikPersonSyncService.SyncResult(true, 42, 40, 3, 1, 2);
            assertThat(done.summary())
                    .isEqualTo("42 on the terminal, 40 matched (3 new, 1 changed), 2 unmatched");
        }
    }

    @Nested
    @DisplayName("Naming a person from what the terminal holds")
    class PersonNames {

        @Test
        @DisplayName("First and last are joined")
        void joined() {
            assertThat(new HikPerson("1", "EMP001", "Leong", "Wei").displayName())
                    .isEqualTo("Leong Wei");
        }

        @Test
        @DisplayName("A half-filled name does not gain a stray space")
        void halfFilled() {
            // The guide's own worked example has lastName as an empty string.
            assertThat(new HikPerson("1", "EMP001", "Leong", "").displayName())
                    .isEqualTo("Leong");
            assertThat(new HikPerson("1", "EMP001", null, "Wei").displayName())
                    .isEqualTo("Wei");
        }

        @Test
        @DisplayName("A person with no name at all is still describable")
        void noName() {
            // This lands on the unmatched-punch screen, where a blank row would
            // tell whoever is fixing it nothing.
            assertThat(new HikPerson("1", null, null, null).displayName())
                    .isEqualTo("(unnamed)");
        }
    }
}
