package com.pixous.hrportal.modules.payroll.dto;

import java.math.BigDecimal;

/** An employee's active salary structure with the computed monthly gross. */
public record SalaryStructureResponse(
        Long userId,
        String employeeName,
        String employeeCode,
        BigDecimal basicSalary,
        BigDecimal hra,
        BigDecimal allowances,
        BigDecimal pfPercentage,
        boolean esiApplicable,
        BigDecimal ptAmount,
        BigDecimal grossSalary
) {}
