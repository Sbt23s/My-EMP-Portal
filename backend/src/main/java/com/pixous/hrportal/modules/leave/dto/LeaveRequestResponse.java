package com.pixous.hrportal.modules.leave.dto;

import com.pixous.hrportal.modules.leave.LeaveRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveRequestResponse(
        Long id, Long userId, String employeeName, Long leaveTypeId, String leaveTypeName,
        LocalDate fromDate, LocalDate toDate, BigDecimal workingDays, String reason,
        String attachmentPath, String status, Long decidedBy, LocalDateTime decidedAt,
        String decisionComment, LocalDateTime createdAt,
        String applicantRole, boolean canAct,
        String requestedToName, String decidedByName,
        String team, String employeeCode,
        /** Role of whoever it went to, and of whoever decided — shown beside their names. */
        String requestedToRole, String decidedByRole,
        /**
         * Who it was sent to, by id.
         *
         * The name and the role were both here and the id was not, so a screen
         * asking "is this one mine" had nothing to compare against -- the
         * approvals page filtered on a field that did not exist, and its
         * Assigned to me tab read zero however many requests were waiting.
         */
        Long requestedTo
) {
    public static LeaveRequestResponse from(LeaveRequest r, String employeeName, String leaveTypeName) {
        return from(r, employeeName, leaveTypeName, null, false);
    }

    public static LeaveRequestResponse from(LeaveRequest r, String employeeName, String leaveTypeName,
                                            String applicantRole, boolean canAct) {
        return from(r, employeeName, leaveTypeName, applicantRole, canAct, null, null);
    }

    public static LeaveRequestResponse from(LeaveRequest r, String employeeName, String leaveTypeName,
                                            String applicantRole, boolean canAct,
                                            String requestedToName, String decidedByName) {
        return from(r, employeeName, leaveTypeName, applicantRole, canAct, requestedToName, decidedByName, null);
    }

    public static LeaveRequestResponse from(LeaveRequest r, String employeeName, String leaveTypeName,
                                            String applicantRole, boolean canAct,
                                            String requestedToName, String decidedByName,
                                            String team) {
        return from(r, employeeName, leaveTypeName, applicantRole, canAct, requestedToName, decidedByName, team, null);
    }

    public static LeaveRequestResponse from(LeaveRequest r, String employeeName, String leaveTypeName,
                                            String applicantRole, boolean canAct,
                                            String requestedToName, String decidedByName,
                                            String team, String employeeCode) {
        return from(r, employeeName, leaveTypeName, applicantRole, canAct,
                requestedToName, decidedByName, team, employeeCode, null, null);
    }

    public static LeaveRequestResponse from(LeaveRequest r, String employeeName, String leaveTypeName,
                                            String applicantRole, boolean canAct,
                                            String requestedToName, String decidedByName,
                                            String team, String employeeCode,
                                            String requestedToRole, String decidedByRole) {
        return new LeaveRequestResponse(r.getId(), r.getUserId(), employeeName,
                r.getLeaveTypeId(), leaveTypeName, r.getFromDate(), r.getToDate(),
                r.getWorkingDays(), r.getReason(), r.getAttachmentPath(), r.getStatus(),
                r.getDecidedBy(), r.getDecidedAt(), r.getDecisionComment(), r.getCreatedAt(),
                applicantRole, canAct, requestedToName, decidedByName, team, employeeCode,
                requestedToRole, decidedByRole, r.getRequestedTo());
    }
}
