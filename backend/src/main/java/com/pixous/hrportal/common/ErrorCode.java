package com.pixous.hrportal.common;

import org.springframework.http.HttpStatus;

/** Canonical application error codes mapped to HTTP statuses. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    GEOFENCE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY),
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
