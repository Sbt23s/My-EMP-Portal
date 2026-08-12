package com.pixous.hrportal.modules.payroll;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payroll_runs")
public class PayrollRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pay_month", nullable = false)
    private Integer payMonth;

    @Column(name = "pay_year", nullable = false)
    private Integer payYear;

    @Column(nullable = false, length = 20)
    private String status = "PREVIEW"; // PREVIEW|CONFIRMED|FINANCE_APPROVED|PAID

    @Column(name = "run_by")
    private Long runBy;

    @Column(name = "run_at")
    private LocalDateTime runAt;

    @Column(name = "finance_approved_by")
    private Long financeApprovedBy;

    @Column(name = "finance_approved_at")
    private LocalDateTime financeApprovedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
