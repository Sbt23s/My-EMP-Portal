package com.pixous.hrportal.modules.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PerformanceGoalRepository extends JpaRepository<PerformanceGoal, Long> {
    List<PerformanceGoal> findByUserId(Long userId);
}
