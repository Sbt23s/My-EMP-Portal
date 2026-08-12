package com.pixous.hrportal.modules.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GeneratePayslipRequest(
        @NotNull Long userId,
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull @Min(2000) Integer year,
        Double overtimeHours,
        Double tds,
        Double otherDeductions,
        Double advanceDeduction,
        Double lopDeduction,
        Double performancePay
) {}
