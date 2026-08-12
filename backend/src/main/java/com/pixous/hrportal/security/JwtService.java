package com.pixous.hrportal.security;

import com.pixous.hrportal.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Issues and validates stateless access tokens using jjwt 0.12.x. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final String issuer;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = props.jwt().accessTokenTtlSeconds();
        this.issuer = props.jwt().issuer();
    }

    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return generateAccessToken(userId, username, roles, "USER");
    }

    public String generateAccessToken(Long userId, String username, List<String> roles, String userType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTtlSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .claim("username", username)
                .claim("roles", roles)
                .claim("userType", userType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parse(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public Map<String, Object> claimsAsMap(String token) {
        return parse(token);
    }
}
