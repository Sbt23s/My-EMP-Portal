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

    /**
     * Monthly PF, in rupees, despite the name.
     *
     * <p>PayslipService takes this figure as the deduction as it stands. It is
     * not a percentage and must not be divided by 100 -- doing so on a
     * dashboard estimate turned an ~2,000 deduction into ~540,000 and made
     * every net-pay total read zero. The default of 12.0 is a leftover from
     * when it really was a rate.
     */
    @Column(name = "pf_percentage", nullable = false)
    private BigDecimal pfPercentage = new BigDecimal("12.0");

    @Column(name = "esi_applicable", nullable = false)
    private boolean esiApplicable = true;

    @Column(name = "pt_amount", nullable = false)
    private BigDecimal ptAmount = BigDecimal.ZERO;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /**
     * Components a payslip itemises.
     *
     * <p>{@code allowances} above is kept and keeps its meaning -- the five
     * structures already stored put a lump sum there, and nobody knows how much
     * of it was conveyance. These are for structures configured from now on, and
     * default to zero so an existing one behaves exactly as it did.
     */
    @Column(name = "conveyance_allowance", precision = 12, scale = 2)
    private BigDecimal conveyanceAllowance = BigDecimal.ZERO;

    @Column(name = "special_allowance", precision = 12, scale = 2)
    private BigDecimal specialAllowance = BigDecimal.ZERO;

    /** A recurring bonus. A one-off month bonus belongs on SalaryMonth. */
    @Column(precision = 12, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal overtime = BigDecimal.ZERO;

    /**
     * Monthly TDS as an amount, not a rate.
     *
     * <p>It is decided per employee from their declarations rather than by a
     * formula.
     *
     * <p>Note that {@code pfPercentage} above is also an amount in rupees
     * despite its name: PayslipService uses it as the deduction directly, with
     * no division. The column name is left alone because renaming it would
     * touch every reader for no behavioural gain, but nothing should divide it
     * by 100.
     */
    @Column(name = "tds_amount", precision = 12, scale = 2)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "other_deduction", precision = 12, scale = 2)
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
