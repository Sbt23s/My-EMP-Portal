package com.pixous.hrportal.modules.leave.dto;

import com.pixous.hrportal.modules.leave.LeaveType;

public record LeaveTypeResponse(
        Long id, String name, String code, Integer maxDaysPerYear,
        boolean carryForward, boolean encashable, String genderRestriction,
        boolean allowPastDates, Integer minNoticeDays, Integer monthlyLimit,
        boolean paid
) {
    public static LeaveTypeResponse from(LeaveType t) {
        return new LeaveTypeResponse(t.getId(), t.getName(), t.getCode(),
                t.getMaxDaysPerYear(), t.isCarryForward(), t.isEncashable(),
                t.getGenderRestriction() != null ? String.valueOf(t.getGenderRestriction()) : null,
                t.isAllowPastDates(), t.getMinNoticeDays(), t.getMonthlyLimit(), t.isPaid());
    }
}
