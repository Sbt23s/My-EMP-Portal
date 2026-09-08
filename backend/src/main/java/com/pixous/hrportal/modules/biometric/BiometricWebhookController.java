package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.config.AppProperties;
import com.pixous.hrportal.modules.biometric.hik.HikWebhookSignature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Where a punch arrives.
 *
 * <p>Hik-Connect posts here when somebody presents a face or a finger at a
 * terminal. It carries no session and no bearer token — it is a machine on the
 * internet calling a public URL — so the signature on each message is the only
 * thing that distinguishes a real punch from one a stranger invented. Every
 * check below exists because skipping it would let somebody write into the
 * attendance register.
 *
 * <p>Two methods on one path, both required by §4.17. The GET is the handshake
 * Hikvision performs before it will create or update the webhook
 * configuration: it presents a batch id and a timestamp and expects them signed
 * back. The POST is the delivery.
 *
 * <h2>Why this answers 200 more often than it looks like it should</h2>
 *
 * <p>A non-2xx tells Hikvision to retry, three times by default. That is right
 * for "we could not store this" and wrong for everything else: a message we
 * have already stored, or one we will never be able to process, would be
 * redelivered until the retries ran out and then discarded. So a duplicate and
 * an unusable payload are both accepted — the work is already done, or can
 * never be done — and only a genuine failure on our side asks for the retry.
 */
@Slf4j
@RestController
@RequestMapping("/api/biometric/webhook")
@Tag(name = "Biometric webhook", description = "Where Hik-Connect delivers punches")
@RequiredArgsConstructor
public class BiometricWebhookController {

    /** §4.17. Named exactly as Hikvision sends them. */
    private static final String HDR_BATCH_ID = "X-Hook-Batch-Id";
    private static final String HDR_SIGNATURE = "X-Hook-Signature";
    private static final String HDR_TIMESTAMP = "X-Hook-Timestamp";

    private final AppProperties props;
    private final BiometricEventIngestService ingestService;

    /**
     * The URL-validation handshake.
     *
     * <p>Hikvision sends this before creating the webhook and again on every
     * update: a GET carrying {@code X-Hook-Batch-Id} and
     * {@code X-Hook-Timestamp}, expecting an {@code X-Hook-Signature} header in
     * reply. Without a valid one the configuration is simply not created, with
     * no error that explains why — so a mistake here presents as "the webhook
     * will not save".
     *
     * <p>The timestamp is deliberately not checked for freshness on this path.
     * It is a challenge we are signing back, not a message we are trusting;
     * rejecting an old one would only break the handshake on a server whose
     * clock has drifted, and there is nothing here to replay.
     */
    @GetMapping
    @Operation(summary = "Answer Hikvision's callback-URL validation challenge")
    public ResponseEntity<String> validate(
            @RequestHeader(value = HDR_BATCH_ID, required = false) String batchId,
            @RequestHeader(value = HDR_TIMESTAMP, required = false) String timestamp) {

        String secret = signingSecret();
        if (secret == null) {
            log.warn("Hikvision tried to validate the webhook URL, but no signing secret "
                    + "is configured (set HIKVISION_WEBHOOK_SECRET or HIKVISION_SECRET_KEY)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Not configured");
        }
        if (batchId == null || timestamp == null) {
            // Not Hikvision, or not the handshake. Nothing to sign.
            log.debug("Webhook validation called without the expected headers");
            return ResponseEntity.badRequest().body("Missing validation headers");
        }

        String signature = HikWebhookSignature.sign(secret, timestamp, batchId);
        log.info("Answered Hikvision's webhook URL validation");
        return ResponseEntity.ok()
                .header(HDR_SIGNATURE, signature)
                .body("OK");
    }

    /**
     * One delivery, which may carry several punches.
     *
     * <p>The five-second timeout in the guide is the reason this does as little
     * as possible: verify, store, answer. Nothing here matches an event to an
     * attendance row or notifies anybody — that happens afterwards, off this
     * thread, because a slow decision made inside the request would turn into a
     * timeout and a retry, and the same punch would arrive again.
     */
    @PostMapping
    @Operation(summary = "Receive punches pushed by Hik-Connect")
    public ResponseEntity<String> receive(
            @RequestHeader(value = HDR_BATCH_ID, required = false) String batchId,
            @RequestHeader(value = HDR_SIGNATURE, required = false) String signature,
            @RequestHeader(value = HDR_TIMESTAMP, required = false) String timestamp,
            @RequestBody(required = false) String rawBody) {

        String secret = signingSecret();
        if (secret == null) {
            /*
             * 503 rather than 401. The message is probably genuine and we
             * cannot tell -- asking Hikvision to retry gives whoever is
             * configuring the server a couple of hours to finish, instead of
             * discarding real punches as forgeries.
             */
            log.warn("A webhook push arrived but no signing secret is configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Not configured");
        }

        if (batchId == null || signature == null || timestamp == null) {
            // A real push always carries all three (§4.17, Table 4-1).
            log.warn("Rejected a webhook push with missing signature headers");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unsigned");
        }

        if (!freshEnough(timestamp)) {
            /*
             * Anti-replay. Without this, a signature captured once stays valid
             * for ever: anybody who recorded a genuine push could send it again
             * tomorrow morning and manufacture an arrival. The guide asks
             * receivers to check freshness for exactly this reason and suggests
             * a minute.
             */
            log.warn("Rejected a webhook push whose timestamp is outside the accepted window");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Stale");
        }

        if (!HikWebhookSignature.matches(secret, timestamp, batchId, signature)) {
            log.warn("Rejected a webhook push whose signature did not verify");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Bad signature");
        }

        try {
            BiometricEventIngestService.IngestResult result =
                    ingestService.ingest(rawBody, batchId);
            log.info("Webhook batch {}: {}", batchId, result.summary());
            // 2XX, so Hikvision stops retrying (§4.17).
            return ResponseEntity.ok(result.summary());
        } catch (Exception e) {
            /*
             * Only here does a retry help: the message was genuine and we
             * failed to keep it. A 5xx asks for it again, which is what we
             * want, because the alternative is a punch that silently never
             * happened.
             */
            log.error("Could not store webhook batch {}: {}", batchId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Retry");
        }
    }

    /**
     * The secret pushes are signed with.
     *
     * <p>The webhook secret when one is set, otherwise the account secret key —
     * which is what Hikvision itself falls back to when {@code signSecret} is
     * left empty at registration (§5.13.2). Keeping the same fallback here
     * means a configuration that omitted it still verifies.
     */
    private String signingSecret() {
        AppProperties.Hikvision h = props.hikvision();
        if (h == null) {
            return null;
        }
        if (h.webhookSecret() != null && !h.webhookSecret().isBlank()) {
            return h.webhookSecret();
        }
        if (h.secretKey() != null && !h.secretKey().isBlank()) {
            return h.secretKey();
        }
        return null;
    }

    /**
     * Whether {@code X-Hook-Timestamp} is close enough to now.
     *
     * <p>Epoch milliseconds, as the guide's demo produces with
     * {@code System.currentTimeMillis()}. The window is applied in both
     * directions: a message from the future is as suspicious as an old one, and
     * a sender whose clock runs fast would otherwise hand out signatures that
     * stay valid for as long as the drift lasts.
     *
     * <p>An unparseable value fails rather than passes. Reading "not a number"
     * as "no opinion" would let a replayed message through by mangling one
     * header.
     */
    private boolean freshEnough(String timestamp) {
        long sent;
        try {
            sent = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            log.warn("Webhook timestamp is not a number");
            return false;
        }
        long toleranceMs = Math.max(1, props.hikvision().webhookToleranceSeconds()) * 1000L;
        long drift = Math.abs(Instant.now().toEpochMilli() - sent);
        return drift <= toleranceMs;
    }
}
