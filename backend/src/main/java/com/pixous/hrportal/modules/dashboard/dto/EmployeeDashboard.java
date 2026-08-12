package com.pixous.hrportal.modules.dashboard.dto;

import com.pixous.hrportal.modules.leave.dto.LeaveBalanceResponse;
import com.pixous.hrportal.modules.notification.NotificationResponse;

import java.time.LocalDateTime;
import java.util.List;

public record EmployeeDashboard(
        String employeeName,
        String employeeCode,
        boolean punchedInToday,
        LocalDateTime punchInAt,
        LocalDateTime punchOutAt,
        Integer workedMinutesToday,
        List<LeaveBalanceResponse> leaveBalances,
        long pendingLeaveRequests,
        long myOpenTickets,
        long myAssets,
        List<NotificationResponse> recentNotifications
) {}
