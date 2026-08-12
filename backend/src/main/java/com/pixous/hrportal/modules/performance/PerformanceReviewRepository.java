package com.pixous.hrportal.modules.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {
    List<PerformanceReview> findByUserId(Long userId);
    List<PerformanceReview> findByManagerId(Long managerId);
}
