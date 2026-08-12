package com.pixous.hrportal.modules.payroll.dto;

import com.pixous.hrportal.modules.payroll.PayslipRequest;

import java.time.LocalDateTime;

/** A payslip request as shown to the requester and to the admin inbox. */
public record PayslipRequestResponse(
        Long id,
        Long userId,
        String employeeName,
        String employeeCode,
        int payMonth,
        int payYear,
        String note,
        String status,
        Long payslipId,
        String decisionNote,
        LocalDateTime decidedAt,
        LocalDateTime createdAt
) {
    public static PayslipRequestResponse from(PayslipRequest r, String name, String code) {
        return new PayslipRequestResponse(
                r.getId(), r.getUserId(), name, code,
                r.getPayMonth(), r.getPayYear(), r.getNote(), r.getStatus(),
                r.getPayslipId(), r.getDecisionNote(), r.getDecidedAt(), r.getCreatedAt());
    }
}
