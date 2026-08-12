package com.pixous.hrportal.modules.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkLeaveDecisionRequest(
        @NotEmpty List<Long> requestIds,
        @NotBlank String decision,
        String comment
) {}
