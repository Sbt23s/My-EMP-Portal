package com.pixous.hrportal.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayslipRequestRepository extends JpaRepository<PayslipRequest, Long> {

    List<PayslipRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PayslipRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<PayslipRequest> findAllByOrderByCreatedAtDesc();

    Optional<PayslipRequest> findByUserIdAndPayMonthAndPayYearAndStatus(
            Long userId, Integer payMonth, Integer payYear, String status);
}
