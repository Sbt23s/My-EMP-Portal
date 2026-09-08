package com.pixous.hrportal.modules.biometric.hik;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The signature on a Hikvision webhook message.
 *
 * <p>This is the only thing standing between the attendance register and
 * anybody who learns the callback URL. The endpoint is necessarily public — the
 * guide requires an HTTPS address reachable from Hikvision's servers — so
 * without a verified signature a stranger could post a punch for any employee,
 * at any time, and it would be indistinguishable from a real one.
 *
 * <p>Implemented from the worked Java example in §4.17 rather than from a
 * description of it, because every detail is load-bearing: the message is
 * {@code timestamp + "." + batchId}, the MAC is HMAC-SHA256 over UTF-8 bytes
 * keyed with the {@code signSecret} registered at
 * {@code /webhook/v1/config/save}, the digest is lower-case hex, and the header
 * value carries a {@code sha256=} prefix. Get any one wrong and every message
 * is rejected — or, far worse, a wrong comparison accepts messages it should
 * not.
 */
public final class HikWebhookSignature {

    private static final String HASH_ALGORITHM = "HmacSHA256";

    /** Fixed by the guide: "Prefix: Hash function identifier (currently sha256)." */
    private static final String PREFIX = "sha256=";

    private HikWebhookSignature() {
    }

    /**
     * The value for an {@code X-Hook-Signature} header.
     *
     * <p>Used in both directions. Hikvision signs the pushes it sends us with
     * this, and the URL-validation handshake requires us to sign a challenge
     * back with the same function — so one implementation serves both and they
     * cannot drift apart.
     *
     * @param signSecret the secret registered with the webhook configuration,
     *                   which defaults to the account's secret key when the
     *                   configuration left it empty
     * @param timestamp  exactly as it arrived in {@code X-Hook-Timestamp}; not
     *                   parsed and re-rendered, because a timestamp that
     *                   round-trips through a number loses any leading zero or
     *                   formatting the sender used, and the signature is over
     *                   the characters rather than the value
     * @param batchId    exactly as it arrived in {@code X-Hook-Batch-Id}
     */
    public static String sign(String signSecret, String timestamp, String batchId) {
        if (signSecret == null || signSecret.isEmpty()) {
            throw new IllegalArgumentException("A signing secret is required");
        }
        String message = timestamp + "." + batchId;
        try {
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(new SecretKeySpec(signSecret.getBytes(StandardCharsets.UTF_8), HASH_ALGORITHM));
            byte[] rawMac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(PREFIX);
            for (byte b : rawMac) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 is required of every JVM, so this cannot happen on a
            // working runtime. Unchecked rather than declared: making every
            // caller handle an impossibility would only add empty catch blocks.
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    /**
     * Whether a presented signature is the one we would have produced.
     *
     * <p>Compared with {@link MessageDigest#isEqual} rather than
     * {@link String#equals}. The difference is timing: {@code equals} returns
     * as soon as two characters differ, so how long a rejection takes reveals
     * how much of the signature was right, and an attacker who can measure that
     * recovers a valid signature one character at a time without ever knowing
     * the secret. {@code isEqual} takes the same time whatever the input.
     *
     * <p>The comparison is deliberately over the whole header value including
     * the {@code sha256=} prefix, so a message that arrives with a different
     * algorithm prefix fails rather than being silently verified as SHA-256.
     */
    public static boolean matches(String signSecret, String timestamp,
                                  String batchId, String presented) {
        if (presented == null || presented.isEmpty()) {
            return false;
        }
        String expected = sign(signSecret, timestamp, batchId);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
