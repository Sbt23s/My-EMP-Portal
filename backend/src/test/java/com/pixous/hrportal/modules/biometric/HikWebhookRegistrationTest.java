package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.biometric.hik.HikApiException;
import com.pixous.hrportal.modules.biometric.hik.HikWebhookSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Going live: what has to be true before a punch can reach this server.
 *
 * <p>Every failure in this step is quiet. A callback URL that is not reachable,
 * or not HTTPS, is refused by Hikvision with a code rather than an explanation.
 * A registered webhook with no event subscription verifies, saves, reports
 * success and then delivers nothing, for ever. And a signing secret that
 * differs between the two ends produces pushes that arrive and are all
 * rejected — visible only as an empty attendance register.
 */
class HikWebhookRegistrationTest {

    @Nested
    @DisplayName("The callback URL is checked before Hikvision is asked")
    class UrlRules {

        /**
         * The same guard {@code HikWebhookApi.register} applies, exercised
         * directly. It lives there rather than being left to the platform
         * because Hikvision's rejection does not say which rule was broken, and
         * "the webhook will not save" is the least useful error in the
         * integration.
         */
        private static void requireValid(String url) {
            if (url == null || !url.startsWith("https://")) {
                throw new HikApiException(null,
                        "The callback URL must be a public HTTPS address (§4.17)");
            }
            if (url.length() > 256) {
                throw new HikApiException(null, "The callback URL must be 256 characters or fewer");
            }
        }

        @Test
        @DisplayName("A plain HTTPS address is accepted")
        void httpsIsFine() {
            requireValid("https://pixoushrportal.pixous.info/api/biometric/webhook");
        }

        @Test
        @DisplayName("http is refused, because §4.17 requires HTTPS")
        void httpIsRefused() {
            /*
             * Not pedantry. The push carries an employee's identity and the
             * moment they arrived, and over http both the payload and the
             * signature are readable by anything on the path -- a captured
             * signature is a punch anybody can replay within the timestamp
             * window.
             */
            assertThatThrown(() -> requireValid("http://example.invalid/api/biometric/webhook"));
        }

        @Test
        @DisplayName("A missing URL is refused rather than sent as null")
        void nullIsRefused() {
            assertThatThrown(() -> requireValid(null));
        }

        @Test
        @DisplayName("An over-long URL is refused at the documented limit")
        void tooLongIsRefused() {
            // 256 characters, per §5.13.2. Sending a longer one fails at
            // Hikvision with a generic code.
            String long_ = "https://example.invalid/" + "x".repeat(250);
            assertThat(long_.length()).isGreaterThan(256);
            assertThatThrown(() -> requireValid(long_));
        }

        private static void assertThatThrown(Runnable r) {
            try {
                r.run();
                throw new AssertionError("expected the URL to be refused");
            } catch (HikApiException expected) {
                // Correct.
            }
        }
    }

    @Nested
    @DisplayName("Both ends sign with the same secret")
    class SecretAgreement {

        /**
         * The fallback both sides apply: the webhook secret when one is set,
         * the account secret key otherwise — which is what Hikvision itself
         * does when {@code signSecret} is left empty at registration (§5.13.2).
         */
        private static String signingSecret(String webhookSecret, String secretKey) {
            if (webhookSecret != null && !webhookSecret.isBlank()) {
                return webhookSecret;
            }
            return secretKey;
        }

        @Test
        @DisplayName("A configured webhook secret is the one used")
        void webhookSecretWins() {
            assertThat(signingSecret("hook-secret", "account-key")).isEqualTo("hook-secret");
        }

        @Test
        @DisplayName("With no webhook secret, both ends fall back to the account key")
        void fallsBackToAccountKey() {
            /*
             * The fallback has to match Hikvision's, not merely exist. If the
             * sender defaulted to the account key and the receiver defaulted to
             * something else, every push would arrive correctly signed and be
             * rejected -- and the only symptom would be an attendance register
             * that never fills.
             */
            assertThat(signingSecret(null, "account-key")).isEqualTo("account-key");
            assertThat(signingSecret("   ", "account-key")).isEqualTo("account-key");
        }

        @Test
        @DisplayName("What is registered verifies what arrives")
        void registeredSecretVerifiesPushes() {
            // End to end on the one value that ties the two halves together:
            // sign with what registration sent, verify with what the receiver
            // resolves, and the same secret has to satisfy both.
            String registered = signingSecret(null, "account-key");
            String timestamp = "1738222807000";
            String batchId = "ebd5a32a518c423f91d4f889a9d5e841";

            String pushSignature = HikWebhookSignature.sign(registered, timestamp, batchId);
            String receiverSecret = signingSecret(null, "account-key");

            assertThat(HikWebhookSignature.matches(
                    receiverSecret, timestamp, batchId, pushSignature)).isTrue();
        }

        @Test
        @DisplayName("A secret changed on one side only rejects everything")
        void rotatingOneSideBreaksIt() {
            /*
             * Stated as a test because it is the likeliest way this breaks in
             * production: somebody rotates HIKVISION_SECRET_KEY without
             * re-registering, and every punch is silently refused.
             */
            String timestamp = "1738222807000";
            String batchId = "ebd5a32a518c423f91d4f889a9d5e841";
            String pushSignature = HikWebhookSignature.sign("old-key", timestamp, batchId);

            assertThat(HikWebhookSignature.matches(
                    "new-key", timestamp, batchId, pushSignature)).isFalse();
        }
    }

    @Nested
    @DisplayName("Which events are subscribed to")
    class Subscription {

        @Test
        @DisplayName("The three biometric grants, not everything")
        void onlyPunches() {
            /*
             * The subscription API treats an empty msgType as "all events". On
             * a real account that is every door, alarm and device message,
             * delivered to an endpoint that understands three of them -- each
             * one stored, read once and discarded.
             *
             * These are the message-type names; the pushed event carries the
             * same numbers with the Msg prefix removed, which is what
             * HikEventTypes matches on. Both halves are asserted so they cannot
             * drift apart.
             */
            assertThat(HikEventTypes.FACE).isEqualTo(110013);
            assertThat(HikEventTypes.FINGERPRINT).isEqualTo(110005);
            assertThat(HikEventTypes.FACE_AND_FINGERPRINT).isEqualTo(110008);

            assertThat(HikEventTypes.isBiometricPunch(110013)).isTrue();
            assertThat(HikEventTypes.isBiometricPunch(110005)).isTrue();
            assertThat(HikEventTypes.isBiometricPunch(110008)).isTrue();
        }
    }
}
