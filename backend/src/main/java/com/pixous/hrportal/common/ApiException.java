package com.pixous.hrportal.common;

/** Single runtime exception type carrying an {@link ErrorCode}; mapped by the global handler. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException business(String message) {
        return new ApiException(ErrorCode.BUSINESS_RULE, message);
    }
}
