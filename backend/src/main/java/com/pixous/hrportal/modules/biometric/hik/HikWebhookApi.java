package com.pixous.hrportal.modules.biometric.hik;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Telling Hik-Connect where to send punches, and which ones to send.
 *
 * <p>Two separate steps, and both are required — this is the part of the
 * integration most likely to look finished while delivering nothing.
 * Registering a callback URL says <em>where</em>; subscribing says
 * <em>what</em>. A registered webhook with no subscription is a URL Hikvision
 * has verified and will never post to, which presents as "the terminal is
 * working but nothing arrives".
 *
 * <p>Called only from the administration endpoint, never automatically. The
 * guide notes that configuring a webhook can stop an existing polling
 * integration from receiving anything and that switching back needs Hikvision's
 * technical support — so it is not something to do on a schedule, or on a
 * deploy, or as a side effect of anything else.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HikWebhookApi {

    private static final String CONFIG_SAVE = "/api/hccgw/webhook/v1/config/save";
    private static final String CONFIG_QUERY = "/api/hccgw/webhook/v1/config/query";
    private static final String CONFIG_DELETE = "/api/hccgw/webhook/v1/config/delete";
    private static final String SUBSCRIBE = "/api/hccgw/rawmsg/v1/mq/subscribe";

    /**
     * The three biometric grants, as message types.
     *
     * <p>Subscribing to everything is the default when {@code msgType} is empty,
     * and it is the wrong default here: it would deliver every door, alarm and
     * device event on the account to an endpoint that only understands punches,
     * and each one would be stored, read once and discarded. These three are
     * what attendance is built on.
     *
     * <p>Written with the {@code Msg} prefix, which is how the subscription API
     * names them — the pushed event then carries the same number without it.
     */
    private static final String[] PUNCH_MESSAGE_TYPES = {
            "Msg110013",   // Access Granted by Face
            "Msg110005",   // Access Granted by Fingerprint
            "Msg110008"    // Access Granted by Face and Fingerprint
    };

    /** §5.13.2: retry attempts, range [-1,5], default 3. */
    private static final int RETRY_TIMES = 3;

    /** Milliseconds between retries. */
    private static final int RETRY_DELAY_MS = 2000;

    private final HikClient client;
    private final ObjectMapper mapper;

    /**
     * Registers the callback URL.
     *
     * <p>Hikvision does not take our word for the address: before saving it, it
     * sends an HTTPS GET carrying a batch id and a timestamp and requires them
     * signed back. If the reply is wrong the configuration is simply not
     * created, and the failure surfaces here as an error code rather than as an
     * explanation — so the usual cause of "the webhook will not save" is that
     * the URL is not reachable from the internet, or is not HTTPS, or is
     * answering with a different signing secret than the one being registered.
     *
     * @param callbackUrl a public HTTPS address answering both GET and POST
     * @param signSecret  the secret pushes will be signed with; the same value
     *                    the receiver verifies against
     */
    public void register(String callbackUrl, String signSecret) {
        if (callbackUrl == null || !callbackUrl.startsWith("https://")) {
            // Checked here rather than left to Hikvision, because its rejection
            // does not say which of several rules was broken.
            throw new HikApiException(null,
                    "The callback URL must be a public HTTPS address (§4.17)");
        }
        if (callbackUrl.length() > 256) {
            throw new HikApiException(null, "The callback URL must be 256 characters or fewer");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("callbackUrl", callbackUrl);
        body.put("retryTimes", RETRY_TIMES);
        body.put("retryDelay", RETRY_DELAY_MS);
        if (signSecret != null && !signSecret.isBlank()) {
            /*
             * Sent explicitly even though Hikvision defaults it to the account
             * secret key. Relying on that default would mean the receiver and
             * the sender agree only by coincidence, and rotating the account
             * secret would silently start rejecting every push.
             */
            body.put("signSecret", signSecret);
        }

        client.call(CONFIG_SAVE, body);
        // The URL, never the secret. A log line is a place a secret leaks to.
        log.info("Registered the Hikvision webhook callback: {}", callbackUrl);
    }

    /** What Hikvision currently believes the callback configuration to be. */
    public JsonNode query() {
        return client.call(CONFIG_QUERY, mapper.createObjectNode());
    }

    /**
     * Removes the callback configuration.
     *
     * <p>Present so a wrong URL can be undone. Only one configuration is allowed
     * per account, so a mistake is not something that can be worked around by
     * adding a second.
     */
    public void unregister() {
        client.call(CONFIG_DELETE, mapper.createObjectNode());
        log.warn("Deleted the Hikvision webhook configuration; punches will stop arriving");
    }

    /**
     * Subscribes to the punch events, or cancels the subscription.
     *
     * <p>The step that is easy to forget, because registering the webhook
     * appears to succeed without it and nothing then arrives.
     *
     * @param on true to subscribe, false to cancel
     */
    public void subscribe(boolean on) {
        ObjectNode body = mapper.createObjectNode();
        body.put("subscribeType", on ? 1 : 0);
        ArrayNode types = body.putArray("msgType");
        for (String t : PUNCH_MESSAGE_TYPES) {
            types.add(t);
        }
        client.call(SUBSCRIBE, body);
        log.info("{} the Hikvision punch subscription ({})",
                on ? "Enabled" : "Cancelled", String.join(", ", PUNCH_MESSAGE_TYPES));
    }
}
