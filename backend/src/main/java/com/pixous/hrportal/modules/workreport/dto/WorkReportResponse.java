package com.pixous.hrportal.modules.workreport.dto;

import com.pixous.hrportal.modules.workreport.WorkReport;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkReportResponse(
        Long id,
        Long userId,
        String employeeName,
        String employeeCode,
        LocalDate workDate,
        String projectName,
        BigDecimal workHours,
        String taskDescription,
        /** Comma-separated attachment paths; null when nothing is attached. */
        String attachments
) {
    public static WorkReportResponse from(WorkReport w, String name, String code) {
        return new WorkReportResponse(
                w.getId(), w.getUserId(), name, code,
                w.getWorkDate(), w.getProjectName(), w.getWorkHours(), w.getTaskDescription(),
                w.getAttachments());
    }
}
