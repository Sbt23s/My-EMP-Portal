package com.pixous.hrportal.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-signup. Login is by username; Aadhaar/phone are optional profile details.
 */
public record SignupRequest(
        @NotBlank @Size(min = 3, max = 60) String username,
        @NotBlank @Size(max = 150) String name,
        String dob,
        String gender,
        @Pattern(regexp = "^$|\\d{12}", message = "Aadhaar must be 12 digits") String aadhar,
        @Pattern(regexp = "^$|\\d{10,15}", message = "Invalid phone") String phone,
        String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        // address block
        String careOf, String house, String street, String locality, String vtc,
        String district, String state, String country, String pincode, String postOffice,
        // optional employment metadata
        String industry,
        Long departmentId,
        Long designationId,
        Long officeLocationId
) {}
