package com.pixous.hrportal.modules.payroll.dto;

import com.pixous.hrportal.modules.payroll.Payslip;

import java.math.BigDecimal;

public record PayslipSummary(
        Long id, int payMonth, int payYear,
        BigDecimal grossSalary, BigDecimal netPay, String pdfPath,

        /**
         * Whether the payslip reached the employee: NOT_SENT, SENT or FAILED.
         *
         * <p>On the summary and not only the full payslip because this is what
         * the list is read for. Every row on the payroll table said "Paid" as
         * soon as a payslip existed, so a send that bounced looked exactly like
         * one that arrived, and the person waiting for their payslip was the
         * only one who knew.
         */
        String deliveryStatus,
        String sentTo,
        java.time.LocalDateTime sentAt,
        String sendError
) {
    public static PayslipSummary from(Payslip p) {
        return new PayslipSummary(p.getId(), p.getPayMonth(), p.getPayYear(),
                p.getGrossSalary(), p.getNetPay(), p.getPdfPath(),
                // NOT_SENT rather than null for a row written before the column
                // existed: "never sent" is the truth about it, and a null would
                // render as an empty cell that reads like a bug.
                p.getDeliveryStatus() == null ? "NOT_SENT" : p.getDeliveryStatus(),
                p.getSentTo(), p.getSentAt(), p.getSendError());
    }
}
