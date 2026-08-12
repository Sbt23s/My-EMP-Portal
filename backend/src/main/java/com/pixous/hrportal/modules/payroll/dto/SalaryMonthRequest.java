package com.pixous.hrportal.modules.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** One employee's basic pay for one month. Saving the same month again replaces it. */
public record SalaryMonthRequest(
        @NotNull Long userId,
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull @PositiveOrZero BigDecimal basicSalary
) {}
