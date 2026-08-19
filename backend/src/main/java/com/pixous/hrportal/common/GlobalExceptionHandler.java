package com.pixous.hrportal.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Translates exceptions into the standard {@link ApiResponse} envelope. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Whether to include exception detail in the response. True only while the
     * dev profile is active; production sends a reference instead.
     */
    private final boolean devProfile;

    public GlobalExceptionHandler(@Value("${spring.profiles.active:prod}") String activeProfile) {
        this.devProfile = activeProfile != null && activeProfile.contains("dev");
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getCode().status())
                .body(ApiResponse.fail(ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.fail("Validation failed", fieldErrors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(ErrorCode.BAD_CREDENTIALS.status())
                .body(ApiResponse.fail("Invalid Aadhaar number or password", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.status())
                .body(ApiResponse.fail("You do not have permission to perform this action", null));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurity(SecurityException ex) {
        String msg = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "You do not have permission to perform this action";
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.status())
                .body(ApiResponse.fail(msg, null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String msg = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "Invalid request";
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.fail(msg, null));
    }

    /**
     * A parameter the caller left out is the caller's mistake, not a server fault.
     *
     * These fell through to the catch-all below and came back as 500 with
     * "Unhandled exception" in the log — so `/attendance/me`, `/calendar/events`
     * and `/reports/attendance` all looked broken when they were simply being
     * called without their dates. All three answer correctly once the parameters
     * are supplied.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class,
                       MissingServletRequestPartException.class,
                       HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        // if/else rather than a switch pattern: this module compiles at -source 17.
        String msg;
        if (ex instanceof MissingServletRequestParameterException e) {
            msg = "Required parameter '" + e.getParameterName() + "' is missing";
        } else if (ex instanceof MethodArgumentTypeMismatchException e) {
            msg = "Parameter '" + e.getName() + "' is not in the expected format";
        } else if (ex instanceof MissingServletRequestPartException e) {
            msg = "Required file '" + e.getRequestPartName() + "' is missing";
        } else {
            msg = "The request body could not be read";
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.fail(msg, null));
    }

    /**
     * The caller used the wrong HTTP method, or asked for a path that does not exist.
     *
     * <p>Both fell through to the catch-all below, which answered 500 and wrote an
     * ERROR line with a stack trace. Neither is a server fault: a GET against a
     * POST-only endpoint is a client mistake and the honest answer is 405. Logging
     * them as failures also buries real errors, since anything scanning the site
     * produces a steady stream of them.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("Wrong method for this endpoint: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail("That endpoint does not accept this request method.", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No handler for {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Not found.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        // A correlation id ties what the caller sees to what is in the log, which
        // is the part that was actually useful about the old behaviour.
        String ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled exception [ref={}]", ref, ex);

        // The exception chain used to be returned to the client. It made a failure
        // easy to read from a response -- and it also handed out package names,
        // entity names and primary keys to anyone who could provoke an error:
        //
        //   HttpMessageNotWritableException: Could not initialize proxy
        //   [com.pixous.hrportal.modules.org.Company#1] - no session
        //
        // Kept for the dev profile, where that convenience costs nothing. In
        // production the caller gets the reference and the detail stays in the log.
        String detail = null;
        if (devProfile) {
            StringBuilder sb = new StringBuilder();
            for (Throwable t = ex; t != null && sb.length() < 1500; t = t.getCause()) {
                sb.append(t.getClass().getSimpleName()).append(": ")
                  .append(t.getMessage()).append(" || ");
            }
            detail = sb.toString();
        }
        return ResponseEntity.status(ErrorCode.INTERNAL.status())
                .body(ApiResponse.fail(
                        "Something went wrong. Please try again later. (ref " + ref + ")",
                        detail));
    }
}
