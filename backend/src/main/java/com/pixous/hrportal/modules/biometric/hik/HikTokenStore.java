package com.pixous.hrportal.modules.biometric.hik;

import java.time.Instant;

/**
 * The token a Hikvision session is currently holding, and where to send calls.
 *
 * <p>A value rather than mutable state on the client, so the "is it still
 * usable" question has one answer that cannot be half-updated by another
 * thread mid-refresh.
 *
 * @param accessToken the value sent in the {@code Token} request header
 * @param expiresAt   when Hikvision says it stops working
 * @param areaDomain  the domain the login response nominates for subsequent
 *                    calls; the guide composes later URLs from it, and it need
 *                    not equal the address the login was sent to
 */
public record HikTokenStore(String accessToken, Instant expiresAt, String areaDomain) {

    /**
     * Whether this token should be replaced before being used.
     *
     * <p>The guide gives a validity of seven days, refreshed to a full seven by
     * calling the login API again. The margin is deliberately large relative to
     * the request it protects: a token that expires between the check and the
     * call produces OPEN000006, and rather than let a webhook-triggered lookup
     * fail on a technicality it is cheaper to renew a token that had hours
     * left. Calling the login API early is explicitly supported.
     */
    public boolean needsRefresh(Instant now, long marginSeconds) {
        return accessToken == null
                || accessToken.isBlank()
                || expiresAt == null
                || !now.plusSeconds(marginSeconds).isBefore(expiresAt);
    }
}
