package com.pixous.hrportal.modules.payroll;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "salary_structures")
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "basic_salary", nullable = false)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(name = "pf_percentage", nullable = false)
    private BigDecimal pfPercentage = new BigDecimal("12.0");

    @Column(name = "esi_applicable", nullable = false)
    private boolean esiApplicable = true;

    @Column(name = "pt_amount", nullable = false)
    private BigDecimal ptAmount = BigDecimal.ZERO;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
