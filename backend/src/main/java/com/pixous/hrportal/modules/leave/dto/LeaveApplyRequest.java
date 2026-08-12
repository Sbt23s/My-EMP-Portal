package com.pixous.hrportal.modules.leave.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LeaveApplyRequest(
        @NotNull Long leaveTypeId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        String reason,
        String attachmentPath,
        Long requestedTo
) {}
