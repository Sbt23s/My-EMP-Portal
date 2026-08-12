package com.pixous.hrportal.modules.attendance.dto;

public record AttendanceSummary(
        int month,
        int year,
        long presentDays,
        long wfhDays,
        long lateDays,
        long absentDays,
        int totalOvertimeMinutes,
        /** Minutes lost to late arrivals across the month. */
        int totalLateMinutes,
        /** Working days counted so far — Sundays and holidays excluded. */
        int workingDays
) {}
