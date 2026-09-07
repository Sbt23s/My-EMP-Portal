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

    /**
     * One month's adjustments, kept off the salary structure.
     *
     * <p>Before these existed the only way to give somebody a bonus was to edit
     * their structure, which then changed every following month too. A row here
     * applies to this month and no other.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal overtime = BigDecimal.ZERO;

    @Column(name = "other_earnings", precision = 12, scale = 2)
    private BigDecimal otherEarnings = BigDecimal.ZERO;

    /** Loss of pay for this month. */
    @Column(name = "leave_deduction", precision = 12, scale = 2)
    private BigDecimal leaveDeduction = BigDecimal.ZERO;

    /** Repayment of a salary advance. */
    @Column(name = "advance_deduction", precision = 12, scale = 2)
    private BigDecimal advanceDeduction = BigDecimal.ZERO;

    @Column(name = "other_deduction", precision = 12, scale = 2)
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    /** Why the adjustment was made, for whoever reads the payslip later. */
    @Column(length = 255)
    private String note;
}
