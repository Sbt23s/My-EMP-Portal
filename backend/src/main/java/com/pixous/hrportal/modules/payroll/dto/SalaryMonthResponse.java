package com.pixous.hrportal.modules.payroll.dto;

import java.math.BigDecimal;

/** An employee's recorded basic pay for a month. */
public record SalaryMonthResponse(
        Long userId,
        Integer month,
        Integer year,
        BigDecimal basicSalary
) {}
