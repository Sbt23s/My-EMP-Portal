package com.pixous.hrportal.modules.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusRequest(
        @NotBlank String status,   // OPEN|IN_PROGRESS|AWAITING_PARTS|RESOLVED|CLOSED
        Long assignTo
) {}
