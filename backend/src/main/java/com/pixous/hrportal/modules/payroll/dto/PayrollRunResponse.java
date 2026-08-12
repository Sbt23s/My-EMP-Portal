package com.pixous.hrportal.modules.payroll.dto;

import com.pixous.hrportal.modules.payroll.PayrollRun;
import java.time.LocalDateTime;
import java.util.List;

public record PayrollRunResponse(
        Long id,
        int payMonth,
        int payYear,
        String status,
        Long runBy,
        LocalDateTime runAt,
        Long financeApprovedBy,
        LocalDateTime financeApprovedAt,
        List<PayslipResponse> payslips
) {
    public static PayrollRunResponse from(PayrollRun run, List<PayslipResponse> payslips) {
        return new PayrollRunResponse(
                run.getId(),
                run.getPayMonth(),
                run.getPayYear(),
                run.getStatus(),
                run.getRunBy(),
                run.getRunAt(),
                run.getFinanceApprovedBy(),
                run.getFinanceApprovedAt(),
                payslips
        );
    }
}
