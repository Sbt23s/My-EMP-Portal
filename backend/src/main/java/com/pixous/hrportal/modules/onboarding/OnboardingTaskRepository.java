package com.pixous.hrportal.modules.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, Long> {
    List<OnboardingTask> findByChecklistId(Long checklistId);
}
