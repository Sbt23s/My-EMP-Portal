package com.pixous.hrportal.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryMonthRepository extends JpaRepository<SalaryMonth, Long> {

    Optional<SalaryMonth> findByUserIdAndPayYearAndPayMonth(Long userId, Integer payYear, Integer payMonth);

    List<SalaryMonth> findByPayYearAndPayMonth(Integer payYear, Integer payMonth);

    List<SalaryMonth> findByUserIdOrderByPayYearDescPayMonthDesc(Long userId);
}
