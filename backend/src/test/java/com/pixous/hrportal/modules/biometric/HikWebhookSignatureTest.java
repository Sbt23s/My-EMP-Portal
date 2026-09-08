package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.biometric.hik.HikWebhookSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The signature that decides whether a punch is real.
 *
 * <p>The callback URL is necessarily public — §4.17 requires an HTTPS address
 * Hikvision's servers can reach, and they hold no account here — so this
 * signature is the only thing between the attendance register and anybody who
 * learns the address. A mistake in it does not look like a bug: punches keep
 * arriving and keep being accepted, and some of them were not sent by
 * Hikvision.
 */
class HikWebhookSignatureTest {

    /*
     * The guide's own demo values (§4.17, "Example Code of Signature Algorithm
     * Demo"). The expected digest was produced by compiling the guide's Java
     * verbatim and running it, then checking our implementation prints the same
     * string -- not by writing down what our code happens to produce, which
     * would only prove it agrees with itself.
     */
    private static final String SECRET = "your_secret_key";
    private static final String TIMESTAMP = "1738222807000";
    private static final String BATCH_ID = "ebd5a32a518c423f91d4f889a9d5e841";
    private static final String EXPECTED =
            "sha256=44cff46d3f22c35bca79b024d66f4c90c5152e4a2ca7c118844e16e32ab6b884";

    @Nested
    @DisplayName("It computes what Hikvision computes")
    class MatchesTheGuide {

        @Test
        @DisplayName("The guide's demo inputs produce the guide's demo output")
        void matchesReferenceImplementation() {
            assertThat(HikWebhookSignature.sign(SECRET, TIMESTAMP, BATCH_ID))
                    .isEqualTo(EXPECTED);
        }

        @Test
        @DisplayName("The message is timestamp, then a period, then the batch id")
        void messageOrderMatters() {
            /*
             * Swapping the two halves is the easy mistake, and it produces a
             * perfectly valid-looking signature that never matches. Pinned so
             * the order cannot be "tidied" later.
             */
            String swapped = HikWebhookSignature.sign(SECRET, BATCH_ID, TIMESTAMP);
            assertThat(swapped).isNotEqualTo(EXPECTED);
        }

        @Test
        @DisplayName("The sha256= prefix is part of the value")
        void carriesPrefix() {
            assertThat(HikWebhookSignature.sign(SECRET, TIMESTAMP, BATCH_ID))
                    .startsWith("sha256=");
        }

        @Test
        @DisplayName("The digest is lower-case hex, two characters a byte")
        void lowerCaseHex() {
            // %02x, not %02X. Upper case would be a different string and every
            // comparison would fail.
            String sig = HikWebhookSignature.sign(SECRET, TIMESTAMP, BATCH_ID);
            String hex = sig.substring("sha256=".length());
            assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
        }
    }

    @Nested
    @DisplayName("What it accepts and what it refuses")
    class Verification {

        @Test
        @DisplayName("A genuine signature verifies")
        void genuine() {
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, EXPECTED))
                    .isTrue();
        }

        @Test
        @DisplayName("A signature made with a different secret is refused")
        void wrongSecret() {
            // Somebody who knows the callback URL but not the signing secret.
            String forged = HikWebhookSignature.sign("not_the_secret", TIMESTAMP, BATCH_ID);
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, forged))
                    .isFalse();
        }

        @Test
        @DisplayName("A signature for a different batch is refused")
        void wrongBatch() {
            /*
             * This is the replay defence working with the header check: a
             * signature captured from one delivery cannot be pasted onto
             * another, because the batch id is inside the signed message.
             */
            String other = HikWebhookSignature.sign(SECRET, TIMESTAMP, "a-different-batch");
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, other))
                    .isFalse();
        }

        @Test
        @DisplayName("A signature for a different timestamp is refused")
        void wrongTimestamp() {
            String other = HikWebhookSignature.sign(SECRET, "1738222807001", BATCH_ID);
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, other))
                    .isFalse();
        }

        @Test
        @DisplayName("A missing or empty signature is refused, not treated as absent")
        void missingSignature() {
            // "No signature" must never mean "no check". An unsigned push is
            // exactly what a forgery looks like.
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, null))
                    .isFalse();
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, ""))
                    .isFalse();
        }

        @Test
        @DisplayName("A correct digest under a different algorithm prefix is refused")
        void wrongPrefix() {
            /*
             * The comparison covers the whole header value including the
             * prefix, so a sender cannot present the right bytes while claiming
             * a weaker algorithm and have us verify it as SHA-256.
             */
            String tampered = EXPECTED.replace("sha256=", "md5=");
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, tampered))
                    .isFalse();
        }

        @Test
        @DisplayName("A signature differing in one character is refused")
        void oneCharacterOff() {
            String last = EXPECTED.substring(EXPECTED.length() - 1);
            String flipped = EXPECTED.substring(0, EXPECTED.length() - 1)
                    + ("4".equals(last) ? "5" : "4");
            assertThat(HikWebhookSignature.matches(SECRET, TIMESTAMP, BATCH_ID, flipped))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Refusing to sign with nothing")
    class NoSecret {

        @Test
        @DisplayName("Signing without a secret throws rather than producing a signature")
        void blankSecretThrows() {
            /*
             * An empty key is a legal HMAC key, so this would otherwise return
             * a real-looking signature that any attacker could also compute --
             * a webhook that verifies everything, silently.
             */
            assertThatThrownBy(() -> HikWebhookSignature.sign("", TIMESTAMP, BATCH_ID))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> HikWebhookSignature.sign(null, TIMESTAMP, BATCH_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
