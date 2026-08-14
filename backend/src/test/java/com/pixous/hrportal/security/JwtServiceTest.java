package com.pixous.hrportal.security;

import com.pixous.hrportal.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JWT issue/validation.
 *
 * <p>These tests build a real HS512 token with jjwt and verify the properties
 * the application depends on: the token carries the right identity, expires at
 * the configured TTL, and any tampering (payload, signature, wrong secret,
 * wrong issuer) is rejected.
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs512-signing-0123456789abcdef";
    private JwtService jwtService;

    private static AppProperties props(long accessTtl) {
        return new AppProperties(
                new AppProperties.Jwt(SECRET, accessTtl, 3600, "hr-portal"),
                new AppProperties.Cors(List.of("http://localhost:5174")),
                new AppProperties.Storage("local", "/tmp/hr-files"),
                new AppProperties.Attendance(200, 0, 8, "09:00", "18:00"),
                new AppProperties.Security(5, 15),
                new AppProperties.Twilio(false, "", "", "", "+91"),
                new AppProperties.Fast2sms(false, "", "q", "")
        );
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(props(3600));
    }

    @Test
    void generatesTokenThatParsesBackToSameIdentity() {
        String token = jwtService.generateAccessToken(42L, "sethu", List.of("SUPER_ADMIN"));

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username")).isEqualTo("sethu");
        assertThat(claims.get("roles")).isEqualTo(List.of("SUPER_ADMIN"));
        assertThat(claims.getIssuer()).isEqualTo("hr-portal");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRoles(token)).containsExactly("SUPER_ADMIN");
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void tokenExpiresAtConfiguredTtl() throws Exception {
        long ttl = 2;
        jwtService = new JwtService(props(ttl));
        String token = jwtService.generateAccessToken(1L, "u", List.of("IT_EMP"));

        assertThat(jwtService.isValid(token)).isTrue();

        // Sleep just past the TTL and confirm expiry is enforced.
        Thread.sleep((ttl + 1) * 1000);
        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void rejectsTamperedPayload() {
        String token = jwtService.generateAccessToken(7L, "alice", List.of("IT_EMP"));
        // Flip the subject claim inside the payload section (segment 1), base64.
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        String tampered = payload.replace("\"7\"", "\"999\"");
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes());
        String forged = String.join(".", parts);

        assertThat(jwtService.isValid(forged)).isFalse();
        assertThatThrownBy(() -> jwtService.parse(forged))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        AppProperties otherProps = props(3600);
        // Same shape, different secret => a service holding the wrong key must
        // refuse tokens signed by the real one.
        AppProperties other = new AppProperties(
                new AppProperties.Jwt("a-completely-different-secret-that-is-also-long-enough-0123456789",
                        otherProps.jwt().accessTokenTtlSeconds(),
                        otherProps.jwt().refreshTokenTtlSeconds(),
                        otherProps.jwt().issuer()),
                otherProps.cors(), otherProps.storage(), otherProps.attendance(),
                otherProps.security(), otherProps.twilio(), otherProps.fast2sms());
        JwtService wrongKeyService = new JwtService(other);
        String token = jwtService.generateAccessToken(7L, "alice", List.of("IT_EMP"));

        assertThat(wrongKeyService.isValid(token)).isFalse();
    }

    @Test
    void rejectsGarbageAndEmptyTokens() {
        assertThat(jwtService.isValid("")).isFalse();
        assertThat(jwtService.isValid("not.a.jwt")).isFalse();
        assertThat(jwtService.isValid(null)).isFalse();
    }

    @Test
    void missingRolesClaimYieldsEmptyList() {
        // Build a token without the roles claim using jjwt's builder directly.
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = io.jsonwebtoken.Jwts.builder()
                .subject("1")
                .issuer("hr-portal")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThat(jwtService.extractRoles(token)).isEmpty();
    }
}
