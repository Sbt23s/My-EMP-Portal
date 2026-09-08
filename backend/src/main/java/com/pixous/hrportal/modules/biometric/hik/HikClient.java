package com.pixous.hrportal.modules.biometric.hik;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pixous.hrportal.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every outbound call to Hik-Connect for Teams goes through here.
 *
 * <p>One place, so three rules hold everywhere rather than in whichever caller
 * remembered them: the request budget (§3.1, five per second), the token in the
 * {@code Token} header (§3.2), and the response envelope, where an HTTP 200
 * carrying {@code errorCode} other than {@code "0"} is a failure (§3.3). That
 * last one is the trap — a caller checking only the status code would read
 * every rejection as a success.
 *
 * <p>Nothing here is called until the credentials are supplied and
 * {@code app.hikvision.enabled} is switched on. Until then {@link #isEnabled()}
 * is false and the sync and webhook paths sit idle rather than failing on a
 * schedule.
 */
@Slf4j
@Component
public class HikClient {

    /** {@code POST}, per Chapter 2 step 3. The only call made without a token. */
    private static final String TOKEN_PATH = "/api/hccgw/platform/v1/token/get";

    /**
     * Renew a token with this much life left.
     *
     * <p>Six hours against a seven-day validity. Generous on purpose: the
     * failure it prevents is a webhook-triggered lookup dying on OPEN000006
     * between the check and the call, and the guide explicitly supports calling
     * the login API early to refresh.
     */
    private static final long REFRESH_MARGIN_SECONDS = 6 * 60 * 60;

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HikRateLimiter rateLimiter;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * The current session.
     *
     * <p>Atomic because the scheduled sync and a webhook's follow-up lookup can
     * both find the token stale at once. Both will then log in, which is
     * harmless — the guide's own model is that logging in again refreshes the
     * same session rather than creating a second one — and the last write wins.
     * The alternative, a lock held across a network call, would block the
     * webhook thread behind whatever the sync is doing.
     */
    private final AtomicReference<HikTokenStore> session = new AtomicReference<>();

    public HikClient(AppProperties props, ObjectMapper mapper, HikRateLimiter rateLimiter) {
        this.props = props;
        this.mapper = mapper;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Whether the integration is configured well enough to call anything.
     *
     * <p>Both the switch and the credentials, because either alone is a
     * half-configuration: enabled with no app key produces a login failure
     * every time the sync runs, which fills the log with an error nobody can
     * act on from the message alone.
     */
    public boolean isEnabled() {
        AppProperties.Hikvision h = props.hikvision();
        return h != null
                && h.enabled()
                && h.appKey() != null && !h.appKey().isBlank()
                && h.secretKey() != null && !h.secretKey().isBlank();
    }

    // ------------------------------------------------------------- the token

    /**
     * A usable token, logging in if the held one is missing or near expiry.
     */
    public String token() {
        HikTokenStore current = session.get();
        if (current == null || current.needsRefresh(Instant.now(), REFRESH_MARGIN_SECONDS)) {
            return login().accessToken();
        }
        return current.accessToken();
    }

    /**
     * Exchanges the app key and secret for a token.
     *
     * <p>The one call that carries the credentials in its body. Nothing about
     * the request is logged: a debug line with the body in it would put the
     * secret key in the log file, which is the same exposure as committing it.
     */
    public HikTokenStore login() {
        requireEnabled();
        AppProperties.Hikvision h = props.hikvision();

        ObjectNode body = mapper.createObjectNode();
        body.put("appKey", h.appKey());
        body.put("secretKey", h.secretKey());

        JsonNode data = post(h.baseUrl() + TOKEN_PATH, body, null);

        String accessToken = data.path("accessToken").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new HikApiException(null, "Hikvision returned no access token");
        }

        /*
         * expireTime is an epoch second, not a duration. Treating it as
         * "seconds from now" would put the expiry in 2078 and the token would
         * never be refreshed -- silently, until it stopped working.
         *
         * A value that is absent or already past is replaced with the
         * documented seven days rather than trusted: an expiry in the past
         * makes every call refresh, which burns the request budget.
         */
        long expireEpoch = data.path("expireTime").asLong(0);
        Instant expiresAt = expireEpoch > Instant.now().getEpochSecond()
                ? Instant.ofEpochSecond(expireEpoch)
                : Instant.now().plusSeconds(7L * 24 * 60 * 60);

        /*
         * areaDomain is where subsequent calls belong. The guide composes later
         * URLs from it and it need not be the address the login was sent to, so
         * ignoring it would work right up until Hikvision moved an account to a
         * different regional host.
         */
        String areaDomain = data.path("areaDomain").asText(null);
        String effectiveDomain = (areaDomain == null || areaDomain.isBlank())
                ? h.baseUrl()
                : stripTrailingSlash(areaDomain);

        HikTokenStore store = new HikTokenStore(accessToken, expiresAt, effectiveDomain);
        session.set(store);
        log.info("Hikvision session established; token valid until {}, calls go to {}",
                expiresAt, effectiveDomain);
        return store;
    }

    /** The host subsequent calls should be sent to. */
    public String areaDomain() {
        HikTokenStore current = session.get();
        if (current == null || current.areaDomain() == null) {
            return stripTrailingSlash(props.hikvision().baseUrl());
        }
        return current.areaDomain();
    }

    // ------------------------------------------------------------ the calls

    /**
     * Calls an authenticated endpoint and returns its {@code data} node.
     *
     * <p>Retries exactly once, and only when Hikvision says the token is the
     * problem. A blanket retry would double every failure against a
     * rate-limited API, and retrying a wrong secret key would loop forever.
     *
     * @param path the URI beginning with {@code /api/}, as the guide writes it
     */
    public JsonNode call(String path, ObjectNode body) {
        requireEnabled();
        String url = areaDomain() + path;
        try {
            return post(url, body, token());
        } catch (HikApiException e) {
            if (!e.isTokenProblem()) {
                throw e;
            }
            // The token went stale between the margin check and the call, or
            // was invalidated at Hikvision's end. One fresh login, one retry.
            log.info("Hikvision rejected the token ({}); logging in again", e.getErrorCode());
            login();
            return post(url, body, token());
        }
    }

    /**
     * One HTTP exchange, with the envelope unwrapped.
     *
     * <p>Both failure shapes are turned into {@link HikApiException}: a
     * non-2xx status, and — the one that matters — a 200 whose body carries a
     * non-zero {@code errorCode}. §3.3 makes the second the normal way an API
     * reports a rejection, so treating HTTP 200 as success would read "the
     * person does not exist" as a successful lookup returning nothing.
     */
    private JsonNode post(String url, ObjectNode body, String token) {
        rateLimiter.acquire();

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body == null ? "{}" : body.toString()));
        if (token != null) {
            // The header is named "Token", not Authorization (§3.2).
            req.header("Token", token);
        }

        HttpResponse<String> response;
        try {
            response = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new HikApiException("Could not reach Hikvision at " + safeUrl(url), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HikApiException("Interrupted calling Hikvision", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HikApiException(null,
                    "Hikvision returned HTTP " + response.statusCode() + " for " + safeUrl(url));
        }

        JsonNode root;
        try {
            root = mapper.readTree(response.body());
        } catch (IOException e) {
            throw new HikApiException("Hikvision returned a body that is not JSON", e);
        }

        String errorCode = root.path("errorCode").asText("");
        if (!"0".equals(errorCode)) {
            String message = root.path("message").asText("no message");
            throw new HikApiException(errorCode,
                    "Hikvision " + safeUrl(url) + " failed: " + errorCode + " " + message);
        }

        return root.path("data");
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new HikApiException(null,
                    "Hikvision is not configured: set HIKVISION_ENABLED, HIKVISION_APP_KEY "
                            + "and HIKVISION_SECRET_KEY");
        }
    }

    /** A URL safe to log — the path, never a query string that might carry a key. */
    private static String safeUrl(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
