package com.pixous.hrportal.modules.leave.dto;

import com.pixous.hrportal.modules.leave.PermissionRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PermissionResponse(
        Long id,
        Long userId,
        String employeeName,
        String employeeCode,
        LocalDate requestDate,
        String fromTime,
        String toTime,
        BigDecimal hours,
        String reason,
        /** HIGH | MEDIUM | LOW. */
        String priority,
        String status,
        String decisionComment,
        LocalDateTime createdAt,
        Long requestedTo,
        String requestedToName,
        String decidedByName,
        LocalDateTime decidedAt,
        String team
) {
    public static PermissionResponse of(PermissionRequest p, String name, String code,
                                        String requestedToName, String decidedByName, String team) {
        return new PermissionResponse(p.getId(), p.getUserId(), name, code,
                p.getRequestDate(), p.getFromTime(), p.getToTime(), p.getHours(),
                p.getReason(), p.getPriority(), p.getStatus(), p.getDecisionComment(), p.getCreatedAt(),
                p.getRequestedTo(), requestedToName, decidedByName, p.getDecidedAt(), team);
    }
}
