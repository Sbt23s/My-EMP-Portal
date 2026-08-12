package com.pixous.hrportal.modules.dashboard.dto;

public record ExecutiveDashboard(
        long headcount,
        long presentToday,
        double attendancePercentToday,
        long pendingLeaveApprovals,
        long openTickets,
        long assetsAssigned,
        long assetsInStock,
        java.util.Map<String, Long> departmentBreakdown,
        java.util.List<java.util.Map<String, Object>> monthlyAttendanceTrend,
        java.util.Map<String, Long> leaveUtilization,
        java.util.List<java.util.Map<String, Object>> payrollCosts
) {}
