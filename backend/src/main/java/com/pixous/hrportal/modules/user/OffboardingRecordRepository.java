package com.pixous.hrportal.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OffboardingRecordRepository extends JpaRepository<OffboardingRecord, Long> {
    Optional<OffboardingRecord> findByUserId(Long userId);
}
