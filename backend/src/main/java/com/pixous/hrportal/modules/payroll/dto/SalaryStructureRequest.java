package com.pixous.hrportal.modules.payroll.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Admin-entered salary components for an employee (basic + allowances + statutory deductions). */
public record SalaryStructureRequest(
        @NotNull Long userId,
        @NotNull @PositiveOrZero BigDecimal basicSalary,
        @PositiveOrZero BigDecimal hra,
        @PositiveOrZero BigDecimal allowances,
        @PositiveOrZero BigDecimal pfPercentage,
        Boolean esiApplicable,
        @PositiveOrZero BigDecimal ptAmount,

        // Added alongside the six above. All optional -- a client that posts
        // only the original fields still works and these stay at zero.
        @PositiveOrZero BigDecimal conveyanceAllowance,
        @PositiveOrZero BigDecimal specialAllowance,
        @PositiveOrZero BigDecimal bonus,
        @PositiveOrZero BigDecimal overtime,
        @PositiveOrZero BigDecimal tdsAmount,
        @PositiveOrZero BigDecimal otherDeduction
) {}
