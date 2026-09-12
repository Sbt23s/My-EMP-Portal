package com.pixous.hrportal.modules.leave.dto;

import com.pixous.hrportal.modules.leave.LeaveType;

/**
 * A leave type as the client sees it.
 *
 * <p>Everything the entity stores and an administrator can set. accrualType
 * and active were missing, so the edit form had no way to show them -- a
 * setting that cannot be read cannot be edited, and both are real policy:
 * how an allowance accrues, and whether the type is offered at all.
 */
public record LeaveTypeResponse(
        Long id, String name, String code, Integer maxDaysPerYear,
        boolean carryForward, boolean encashable, String genderRestriction,
        boolean allowPastDates, String accrualType, Integer minNoticeDays,
        Integer monthlyLimit, boolean paid, boolean active
) {
    public static LeaveTypeResponse from(LeaveType t) {
        return new LeaveTypeResponse(t.getId(), t.getName(), t.getCode(),
                t.getMaxDaysPerYear(), t.isCarryForward(), t.isEncashable(),
                t.getGenderRestriction() != null ? String.valueOf(t.getGenderRestriction()) : null,
                t.isAllowPastDates(), t.getAccrualType(), t.getMinNoticeDays(),
                t.getMonthlyLimit(), t.isPaid(), t.isActive());
    }
}
