package com.pixous.hrportal.modules.user.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record OffboardingRequest(
        @NotNull(message = "Relieving date is required") LocalDate relievingDate,
        String reason,
        String notes
) {}
