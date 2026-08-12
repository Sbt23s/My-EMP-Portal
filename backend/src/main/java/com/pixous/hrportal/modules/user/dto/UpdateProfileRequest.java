package com.pixous.hrportal.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Editable profile fields (identity fields like Aadhaar are intentionally immutable). */
public record UpdateProfileRequest(
        @Size(max = 150) String name,
        String dob,
        String gender,
        @Email String email,
        String careOf, String house, String street, String locality, String vtc,
        String district, String state, String country, String pincode, String postOffice
) {}
