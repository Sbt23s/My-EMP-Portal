package com.pixous.hrportal.modules.payroll;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What one employee's basic pay was for one month.
 *
 * <p>{@link SalaryStructure} carries the standing figures; this records the
 * month a figure applied to, so a change part-way through the year does not
 * rewrite what earlier months were paid on. Generating a payslip prefers the row
 * for that month and falls back to the standing basic when there is none.
 */
@Getter
@Setter
@Entity
@Table(name = "salary_months")
public class SalaryMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pay_year", nullable = false)
    private Integer payYear;

    /** 1..12 */
    @Column(name = "pay_month", nullable = false)
    private Integer payMonth;

    @Column(name = "basic_salary", nullable = false)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
