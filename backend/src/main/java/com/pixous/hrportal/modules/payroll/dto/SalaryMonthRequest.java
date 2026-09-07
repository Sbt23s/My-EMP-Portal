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
        @NotNull @PositiveOrZero BigDecimal basicSalary,

        /*
         * One month's adjustments. All optional.
         *
         * Before these existed, giving somebody a bonus meant editing their
         * salary structure, which then paid it again every following month.
         */
        @PositiveOrZero BigDecimal bonus,
        @PositiveOrZero BigDecimal overtime,
        @PositiveOrZero BigDecimal otherEarnings,
        @PositiveOrZero BigDecimal leaveDeduction,
        @PositiveOrZero BigDecimal advanceDeduction,
        @PositiveOrZero BigDecimal otherDeduction,
        String note
) {}
