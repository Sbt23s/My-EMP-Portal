package com.pixous.hrportal.modules.payroll.dto;

import com.pixous.hrportal.modules.payroll.Payslip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PayslipResponse(
        Long id, Long userId, String employeeName, String employeeCode,
        int payMonth, int payYear,
        BigDecimal basicSalary, BigDecimal hra, BigDecimal allowances, BigDecimal overtimePay,
        BigDecimal grossSalary, BigDecimal pfDeduction, BigDecimal esiDeduction,
        BigDecimal ptDeduction, BigDecimal tdsDeduction, BigDecimal otherDeductions,
        BigDecimal totalDeductions, BigDecimal netPay, BigDecimal lopDays,
        String pdfPath, LocalDateTime generatedAt,
        // ---- customizable fields (V15) ----
        String companyName, String companyLogo, String companyGstin, String companyAddress,
        String bankName, String bankAccount, String designation, String department,
        LocalDate payDate, Integer workingDays,
        BigDecimal performancePay, BigDecimal expensesPay,
        BigDecimal salaryAdvance, BigDecimal healthInsurance, String source,
        /** How many times this payslip has been generated. 1 unless it was regenerated. */
        Integer revision,

        // ---- itemised components (V109) ----
        BigDecimal conveyanceAllowance, BigDecimal specialAllowance, BigDecimal bonus,
        BigDecimal otherEarnings, BigDecimal leaveDeduction, BigDecimal advanceDeduction,

        /**
         * Delivery: NOT_SENT | SENT | FAILED, and where it went.
         *
         * <p>Returned so the payroll screen can show whether a payslip actually
         * reached the employee. Before this the send left no trace, and a failed
         * one looked the same as one never attempted.
         */
        String deliveryStatus, String sentTo, LocalDateTime sentAt, String sendError
) {
    public static PayslipResponse from(Payslip p, String name, String code) {
        return new PayslipResponse(p.getId(), p.getUserId(), name, code,
                p.getPayMonth(), p.getPayYear(),
                p.getBasicSalary(), p.getHra(), p.getAllowances(), p.getOvertimePay(),
                p.getGrossSalary(), p.getPfDeduction(), p.getEsiDeduction(),
                p.getPtDeduction(), p.getTdsDeduction(), p.getOtherDeductions(),
                p.getTotalDeductions(), p.getNetPay(), p.getLopDays(),
                p.getPdfPath(), p.getGeneratedAt(),
                p.getCompanyName(), p.getCompanyLogo(), p.getCompanyGstin(), p.getCompanyAddress(),
                p.getBankName(), p.getBankAccount(), p.getDesignation(), p.getDepartment(),
                p.getPayDate(), p.getWorkingDays(),
                p.getPerformancePay(), p.getExpensesPay(),
                p.getSalaryAdvance(), p.getHealthInsurance(), p.getSource(),
                p.getRevision(),
                p.getConveyanceAllowance(), p.getSpecialAllowance(), p.getBonus(),
                p.getOtherEarnings(), p.getLeaveDeduction(), p.getAdvanceDeduction(),
                p.getDeliveryStatus(), p.getSentTo(), p.getSentAt(), p.getSendError());
    }
}
