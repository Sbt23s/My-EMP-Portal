package com.pixous.hrportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Strongly-typed binding for the {@code app.*} settings in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Storage storage,
        Attendance attendance,
        Security security,
        Twilio twilio,
        Fast2sms fast2sms,
        Hikvision hikvision
) {
    /**
     * Hik-Connect for Teams (HikCentral Connect) OpenAPI settings.
     *
     * <p>Every one of these comes from an environment variable and none has a
     * committed default. The app key and secret are the whole of the
     * integration's authority: anybody holding them can read every person and
     * every punch on the customer's account, and Hikvision's own guidance is to
     * contact support for replacements once they leak. They never reach the
     * browser — the React side talks only to this portal.
     *
     * <p>{@code baseUrl} is regional and the wrong one simply fails to
     * authenticate; Singapore/India is {@code https://isgp.hikcentralconnect.com}.
     *
     * <p>{@code webhookSecret} is the {@code signSecret} registered with
     * {@code /webhook/v1/config/save}, which signs every pushed message. The
     * guide lets it default to the secret key when absent, but a separate value
     * is kept here so the signing secret can be rotated without re-issuing the
     * credentials that authenticate outbound calls.
     */
    public record Hikvision(
            boolean enabled,
            String baseUrl,
            String appKey,
            String secretKey,
            String webhookSecret,
            /**
             * How far a push's {@code X-Hook-Timestamp} may sit from now, in
             * seconds. The guide suggests no more than a minute; the value is
             * configurable because a server whose clock drifts would otherwise
             * reject every message with no obvious cause.
             */
            int webhookToleranceSeconds
    ) {}

    /** Twilio SMS settings. Prefer overriding via env vars in production. */
    public record Twilio(
            boolean enabled,
            String accountSid,
            String authToken,
            String fromNumber,
            String defaultCountryCode
    ) {}

    /**
     * Fast2SMS settings — the preferred SMS provider for Indian numbers.
     * The API key must come from an env var; never commit it.
     */
    public record Fast2sms(
            boolean enabled,
            String apiKey,
            String route,
            String senderId
    ) {}

    public record Jwt(
            String secret,
            long accessTokenTtlSeconds,
            long refreshTokenTtlSeconds,
            String issuer
    ) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Storage(String type, String localPath) {}

    public record Attendance(
            int defaultGeofenceRadiusMetres,
            int lateGraceMinutes,
            int standardWorkHours,
            /** Office start, e.g. "09:00". A punch after this counts as late. */
            String officeStart,
            /** Office end, e.g. "18:00". Time worked past this counts as overtime. */
            String officeEnd
    ) {}

    public record Security(int maxFailedLoginAttempts, int accountLockMinutes) {}
}
