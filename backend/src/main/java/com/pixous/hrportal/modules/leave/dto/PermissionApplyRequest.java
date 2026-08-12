package com.pixous.hrportal.modules.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PermissionApplyRequest(
        @NotNull LocalDate requestDate,
        @NotBlank String fromTime,
        @NotBlank String toTime,
        String reason,
        Long requestedTo,
        /** HIGH | MEDIUM | LOW. Missing reads as MEDIUM. */
        String priority
) {}
