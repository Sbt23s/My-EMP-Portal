package com.pixous.hrportal.common;

import com.pixous.hrportal.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Sends SMS via the Twilio REST API using the JDK HTTP client (no extra
 * dependency). Fire-and-forget: runs async and never throws to the caller, so
 * a delivery failure never blocks or breaks the business action that triggered it.
 */
@Slf4j
@Service
public class SmsService {

    private final AppProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public SmsService(AppProperties props) {
        this.props = props;
    }

    /** Normalise a raw phone number to E.164 (e.g. "9047699216" -> "+919047699216"). */
    public String toE164(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty()) return null;
        if (cleaned.startsWith("+")) return cleaned;
        cleaned = cleaned.replaceFirst("^0+", "");
        String cc = props.twilio() != null && props.twilio().defaultCountryCode() != null
                ? props.twilio().defaultCountryCode() : "+91";
        if (cleaned.length() == 10) return cc + cleaned;
        return "+" + cleaned; // already includes a country code
    }

    /**
     * Say once, at startup, which provider will actually be used. Without this a
     * missing API key looks identical to a working setup until someone waits for
     * an SMS that never arrives.
     */
    @jakarta.annotation.PostConstruct
    void logProvider() {
        AppProperties.Fast2sms f2s = props.fast2sms();
        boolean keyed = f2s != null && f2s.apiKey() != null && !f2s.apiKey().isBlank();
        if (f2s != null && f2s.enabled() && keyed) {
            log.info("SMS provider: Fast2SMS (route={}, key ...{})",
                    f2s.route(), f2s.apiKey().substring(Math.max(0, f2s.apiKey().length() - 4)));
        } else if (f2s != null && f2s.enabled()) {
            log.warn("SMS provider: Fast2SMS is enabled but FAST2SMS_API_KEY is EMPTY — "
                    + "falling back to Twilio. No SMS will be sent until the key is set.");
        } else {
            log.info("SMS provider: Twilio (Fast2SMS disabled)");
        }
    }

    /**
     * Send an SMS. Fast2SMS is used when configured (the provider for Indian
     * numbers); Twilio remains as a fallback. Never throws — a delivery
     * failure must not break the action that triggered it.
     */
    @Async
    public void send(String toRaw, String body) {
        AppProperties.Fast2sms f2s = props.fast2sms();
        if (f2s != null && f2s.enabled() && f2s.apiKey() != null && !f2s.apiKey().isBlank()) {
            sendViaFast2Sms(f2s, toRaw, body);
            return;
        }
        sendViaTwilio(toRaw, body);
    }

    /**
     * Fast2SMS takes plain 10-digit Indian mobile numbers. Only strips a known
     * prefix (+91 / 91 / leading 0); anything else that is not exactly ten
     * digits starting 6–9 is rejected rather than guessed at, so a mistyped
     * number never results in an SMS to a stranger.
     */
    private String toLocal10(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");

        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        else if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);

        boolean valid = digits.length() == 10 && digits.charAt(0) >= '6' && digits.charAt(0) <= '9';
        if (!valid) {
            log.warn("SMS skipped — '{}' is not a valid 10-digit Indian mobile number", raw);
            return null;
        }
        return digits;
    }

    private void sendViaFast2Sms(AppProperties.Fast2sms cfg, String toRaw, String body) {
        String to = toLocal10(toRaw);
        if (to == null) {
            log.warn("SMS skipped — recipient has no valid 10-digit number");
            return;
        }
        try {
            StringBuilder form = new StringBuilder()
                    .append("route=").append(enc(cfg.route() == null || cfg.route().isBlank() ? "q" : cfg.route()))
                    .append("&message=").append(enc(body))
                    .append("&language=english")
                    .append("&flash=0")
                    .append("&numbers=").append(enc(to));
            if (cfg.senderId() != null && !cfg.senderId().isBlank()) {
                form.append("&sender_id=").append(enc(cfg.senderId()));
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.fast2sms.com/dev/bulkV2"))
                    .header("authorization", cfg.apiKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("SMS sent to {} (Fast2SMS HTTP {})", to, resp.statusCode());
            } else {
                log.warn("SMS to {} failed — Fast2SMS HTTP {}: {}", to, resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("SMS send error to {}: {}", to, e.getMessage());
        }
    }

    private void sendViaTwilio(String toRaw, String body) {
        AppProperties.Twilio tw = props.twilio();
        if (tw == null || !tw.enabled()) {
            log.info("SMS disabled — skipping message to {}", toRaw);
            return;
        }
        if (tw.accountSid() == null || tw.accountSid().isBlank()
                || tw.authToken() == null || tw.authToken().isBlank()) {
            log.warn("SMS skipped — Twilio credentials not configured");
            return;
        }
        String to = toE164(toRaw);
        if (to == null) {
            log.warn("SMS skipped — recipient has no valid phone number");
            return;
        }
        try {
            String form = "To=" + enc(to) + "&From=" + enc(tw.fromNumber()) + "&Body=" + enc(body);
            String basic = Base64.getEncoder().encodeToString(
                    (tw.accountSid() + ":" + tw.authToken()).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                            + tw.accountSid() + "/Messages.json"))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("SMS sent to {} (Twilio HTTP {})", to, resp.statusCode());
            } else {
                log.warn("SMS to {} failed — Twilio HTTP {}: {}", to, resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("SMS send error to {}: {}", to, e.getMessage());
        }
    }

    /**
     * Send the same message to many people in one call — Fast2SMS accepts a
     * comma-separated list, so an org-wide announcement costs one request
     * instead of one per employee.
     */
    @Async
    public void sendBulk(java.util.Collection<String> rawNumbers, String body) {
        if (rawNumbers == null || rawNumbers.isEmpty()) return;

        AppProperties.Fast2sms cfg = props.fast2sms();
        if (cfg == null || !cfg.enabled() || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            // No bulk provider configured — fall back to one message each.
            rawNumbers.forEach(n -> send(n, body));
            return;
        }

        java.util.List<String> numbers = rawNumbers.stream()
                .map(this::toLocal10)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (numbers.isEmpty()) return;

        // Fast2SMS caps recipients per request; send in batches.
        final int BATCH = 100;
        for (int i = 0; i < numbers.size(); i += BATCH) {
            String csv = String.join(",", numbers.subList(i, Math.min(i + BATCH, numbers.size())));
            try {
                StringBuilder form = new StringBuilder()
                        .append("route=").append(enc(cfg.route() == null || cfg.route().isBlank() ? "q" : cfg.route()))
                        .append("&message=").append(enc(body))
                        .append("&language=english")
                        .append("&flash=0")
                        .append("&numbers=").append(enc(csv));
                if (cfg.senderId() != null && !cfg.senderId().isBlank()) {
                    form.append("&sender_id=").append(enc(cfg.senderId()));
                }
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.fast2sms.com/dev/bulkV2"))
                        .header("authorization", cfg.apiKey())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.info("Bulk SMS sent to {} recipients (Fast2SMS HTTP {})",
                            csv.split(",").length, resp.statusCode());
                } else {
                    log.warn("Bulk SMS failed — Fast2SMS HTTP {}: {}", resp.statusCode(), resp.body());
                }
            } catch (Exception e) {
                log.error("Bulk SMS send error: {}", e.getMessage());
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
