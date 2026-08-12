package com.pixous.hrportal.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Uniform envelope returned by every REST endpoint so the web and mobile
 * clients can rely on a single, predictable shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Object errors,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> message(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(String message, Object errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now());
    }
}
