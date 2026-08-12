package com.pixous.hrportal.modules.payroll.dto;

import com.pixous.hrportal.modules.payroll.Payslip;

import java.math.BigDecimal;

public record PayslipSummary(
        Long id, int payMonth, int payYear,
        BigDecimal grossSalary, BigDecimal netPay, String pdfPath
) {
    public static PayslipSummary from(Payslip p) {
        return new PayslipSummary(p.getId(), p.getPayMonth(), p.getPayYear(),
                p.getGrossSalary(), p.getNetPay(), p.getPdfPath());
    }
}
