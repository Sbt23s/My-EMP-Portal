package com.pixous.hrportal.modules.auth.dto;

/** Access + refresh tokens returned on successful auth / refresh. */
public record TokenPair(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {}
