package com.pixous.hrportal.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Login by username + password. */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}
