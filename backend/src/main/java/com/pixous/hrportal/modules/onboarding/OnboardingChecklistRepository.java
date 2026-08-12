package com.pixous.hrportal.modules.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OnboardingChecklistRepository extends JpaRepository<OnboardingChecklist, Long> {
    Optional<OnboardingChecklist> findByUserId(Long userId);

    java.util.List<OnboardingChecklist> findByStatus(String status);
}
