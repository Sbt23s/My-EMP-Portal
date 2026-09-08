package com.pixous.hrportal.modules.biometric.hik;

import lombok.Getter;

/**
 * A call to Hikvision that did not succeed.
 *
 * <p>Carries the platform's own error code, because the code decides what the
 * caller should do and the message does not. The ones that matter here:
 *
 * <ul>
 *   <li>{@code OPEN000006} token expired and {@code OPEN000007} token exception
 *       — recoverable by logging in again, which {@link HikClient} does once
 *       before giving up.
 *   <li>{@code OPEN000001} AK does not exist, {@code OPEN000002} incorrect SK —
 *       a configuration error. Retrying cannot fix it and would only spend the
 *       request budget, so these must not be retried.
 *   <li>{@code OPEN000009} network exception, {@code OPEN000018} server error —
 *       Hikvision's end. Worth another attempt later, never immediately.
 * </ul>
 */
@Getter
public class HikApiException extends RuntimeException {

    /** Token expired. Recoverable by logging in again. */
    public static final String TOKEN_EXPIRED = "OPEN000006";
    /** Token exception. Also recoverable by logging in again. */
    public static final String TOKEN_INVALID = "OPEN000007";
    /** The app key does not exist. A configuration error, not a transient one. */
    public static final String AK_NOT_FOUND = "OPEN000001";
    /** Incorrect secret key. A configuration error. */
    public static final String SK_INCORRECT = "OPEN000002";

    private final String errorCode;

    public HikApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public HikApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    /**
     * Whether logging in again might fix this.
     *
     * <p>Only the two token codes. Widening this to "any failure" would turn a
     * wrong secret key into an endless login loop against a rate-limited API.
     */
    public boolean isTokenProblem() {
        return TOKEN_EXPIRED.equals(errorCode) || TOKEN_INVALID.equals(errorCode);
    }

    /**
     * Whether the credentials themselves are wrong.
     *
     * <p>Worth separating because it is the one failure an administrator can
     * act on, and because it must stop the integration rather than have it
     * retry on a schedule for a week.
     */
    public boolean isCredentialProblem() {
        return AK_NOT_FOUND.equals(errorCode) || SK_INCORRECT.equals(errorCode);
    }
}
