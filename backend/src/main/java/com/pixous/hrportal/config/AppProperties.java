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
        Fast2sms fast2sms
) {
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
