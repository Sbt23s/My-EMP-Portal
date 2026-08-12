package com.pixous.hrportal.modules.leave.dto;

import jakarta.validation.constraints.NotBlank;

public record LeaveDecisionRequest(
        @NotBlank String decision,
        String comment
) {}
