package com.pixous.hrportal.modules.leave.dto;

import jakarta.validation.constraints.NotBlank;

public record LeaveTypeRequest(
        @NotBlank String name,
        @NotBlank String code,
        Integer maxDaysPerYear,
        boolean carryForward,
        boolean encashable,
        String genderRestriction,
        boolean allowPastDates,
        String accrualType,
        Integer minNoticeDays,
        Integer monthlyLimit,
        boolean paid
) {}
