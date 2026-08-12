package com.pixous.hrportal.modules.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Payload an employee/HR/manager sends to request a payslip for a month. */
public record CreatePayslipRequestDto(
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull @Min(2000) Integer year,
        String note
) {}
