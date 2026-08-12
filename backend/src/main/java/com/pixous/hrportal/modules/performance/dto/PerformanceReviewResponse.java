package com.pixous.hrportal.modules.performance.dto;

import java.time.LocalDateTime;

public record PerformanceReviewResponse(
        Long id,
        Long userId,
        Long managerId,
        String reviewPeriod,
        Integer selfRating,
        String selfComment,
        Integer managerRating,
        String managerComment,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
