package com.pixous.hrportal.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    Optional<PayrollRun> findByPayMonthAndPayYear(int month, int year);
}
