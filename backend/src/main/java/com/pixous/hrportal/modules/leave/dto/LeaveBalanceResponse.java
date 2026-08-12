package com.pixous.hrportal.modules.leave.dto;

import java.math.BigDecimal;

public record LeaveBalanceResponse(
        Long leaveTypeId, String leaveTypeName, String leaveTypeCode,
        int year, BigDecimal allocated, BigDecimal used, BigDecimal available
) {}
