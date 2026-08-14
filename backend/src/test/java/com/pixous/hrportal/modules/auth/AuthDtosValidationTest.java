package com.pixous.hrportal.modules.auth;

import com.pixous.hrportal.modules.auth.dto.ChangePasswordRequest;
import com.pixous.hrportal.modules.auth.dto.LoginRequest;
import com.pixous.hrportal.modules.auth.dto.SignupRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-validation tests for the authentication request DTOs.
 *
 * <p>These mirror what Spring's {@code @Valid} enforces at the controller
 * boundary: blank usernames, short passwords, malformed Aadhaar and phone
 * numbers must all be rejected before any service code runs.
 */
class AuthDtosValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void loginRequiresBothFields() {
        Set<ConstraintViolation<LoginRequest>> violations =
                validator.validate(new LoginRequest("", ""));
        assertThat(violations).hasSize(2);
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("username", "password");
    }

    @Test
    void loginAcceptsValidCredentials() {
        assertThat(validator.validate(new LoginRequest("admin", "pass1234"))).isEmpty();
    }

    @Test
    void changePasswordRequiresNewPasswordAtLeastEightChars() {
        assertThat(validator.validate(new ChangePasswordRequest("oldpass", "short"))).isNotEmpty();
        assertThat(validator.validate(new ChangePasswordRequest("oldpass", "eightchars"))).isEmpty();
        // Blank old password is rejected too.
        assertThat(validator.validate(new ChangePasswordRequest("", "eightchars"))).isNotEmpty();
    }

    @Test
    void signupRejectsShortUsernameAndPassword() {
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(signup(
                "ab", "Name", null, null, null, null, "pass"));
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("username", "password");
    }

    @Test
    void signupRejectsMalformedAadhaar() {
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(signup(
                "validuser", "Name", "12345", "9876543210", null, null, "pass1234"));
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("aadhar");
    }

    @Test
    void signupRejectsMalformedPhone() {
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(signup(
                "validuser", "Name", null, "not-a-phone", null, null, "pass1234"));
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("phone");
    }

    @Test
    void signupAcceptsValidPayload() {
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(signup(
                "validuser", "Valid Name", "123456789012", "9876543210",
                "user@example.com", "IT", "pass1234"));
        assertThat(violations).isEmpty();
    }

    /** username, name, aadhar, phone, email, industry, password — in record order. */
    private SignupRequest signup(String username, String name, String aadhar, String phone,
                                 String email, String industry, String password) {
        return new SignupRequest(
                username, name,
                null, null, aadhar, phone, email,
                password,
                null, null, null, null, null, null, null, null, null, null,
                industry,
                null, null, null
        );
    }
}
