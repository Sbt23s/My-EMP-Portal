package com.pixous.hrportal.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByUserIdOrderByPayYearDescPayMonthDesc(Long userId);
    Optional<Payslip> findByUserIdAndPayMonthAndPayYear(Long userId, int month, int year);

    List<Payslip> findByPayMonthAndPayYear(int month, int year);
    boolean existsByUserIdAndPayMonthAndPayYear(Long userId, int month, int year);
    List<Payslip> findByPayrollRunId(Long payrollRunId);
}
