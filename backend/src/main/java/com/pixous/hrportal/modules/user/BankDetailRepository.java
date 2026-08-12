package com.pixous.hrportal.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankDetailRepository extends JpaRepository<BankDetail, Long> {
    List<BankDetail> findByUserId(Long userId);
    Optional<BankDetail> findByIdAndUserId(Long id, Long userId);
}
