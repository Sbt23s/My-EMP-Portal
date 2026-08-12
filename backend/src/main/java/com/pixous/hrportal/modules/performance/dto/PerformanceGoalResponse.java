package com.pixous.hrportal.modules.performance.dto;

import java.time.LocalDateTime;

public record PerformanceGoalResponse(
        Long id,
        Long userId,
        String title,
        String description,
        int progress,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
