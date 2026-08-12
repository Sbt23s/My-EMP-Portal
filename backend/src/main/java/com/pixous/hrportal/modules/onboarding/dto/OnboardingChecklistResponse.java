package com.pixous.hrportal.modules.onboarding.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OnboardingChecklistResponse(
        Long id,
        Long userId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<OnboardingTaskResponse> tasks
) {}
