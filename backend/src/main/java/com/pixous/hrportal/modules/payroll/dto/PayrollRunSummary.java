package com.pixous.hrportal.modules.payroll.dto;

import com.pixous.hrportal.modules.payroll.PayrollRun;
import java.time.LocalDateTime;

public record PayrollRunSummary(
        Long id,
        int payMonth,
        int payYear,
        String status,
        LocalDateTime createdAt
) {
    public static PayrollRunSummary from(PayrollRun run) {
        return new PayrollRunSummary(
                run.getId(),
                run.getPayMonth(),
                run.getPayYear(),
                run.getStatus(),
                run.getCreatedAt()
        );
    }
}
