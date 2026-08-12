package com.pixous.hrportal.modules.payroll.dto;

import java.math.BigDecimal;

/**
 * The customizable payslip form an admin fills when approving a payslip
 * request. Every field an ordinary company payslip carries is editable here
 * (company identity, employee identity, and each earning/deduction line) so
 * the generated PDF matches the company's own format. Numeric fields default
 * to zero when omitted.
 */
public record ApprovePayslipRequestDto(
        // ----- Company identity -----
        String companyName,
        String companyGstin,
        String companyAddress,
        // logo is uploaded separately (multipart) and referenced by path
        String companyLogo,

        // ----- Employee identity (editable overrides) -----
        String employeeName,
        String employeeCode,
        String designation,
        String department,
        String bankName,
        String bankAccount,

        // ----- Pay period -----
        String payDate,        // ISO yyyy-MM-dd; optional
        Integer workingDays,
        BigDecimal lopDays,

        // ----- Earnings -----
        BigDecimal basicSalary,
        BigDecimal hra,
        BigDecimal allowances,
        BigDecimal overtimePay,
        BigDecimal performancePay,
        BigDecimal expensesPay,

        // ----- Deductions -----
        BigDecimal pfDeduction,
        BigDecimal esiDeduction,
        BigDecimal ptDeduction,
        BigDecimal tdsDeduction,
        BigDecimal healthInsurance,
        BigDecimal salaryAdvance,
        BigDecimal otherDeductions,

        // ----- Decision -----
        String decisionNote
) {}
