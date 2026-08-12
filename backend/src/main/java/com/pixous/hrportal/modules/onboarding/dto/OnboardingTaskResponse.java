package com.pixous.hrportal.modules.onboarding.dto;

import java.time.LocalDateTime;

public record OnboardingTaskResponse(
        Long id,
        String taskName,
        String description,
        boolean isCompleted,
        LocalDateTime completedAt
) {}
