package com.pixous.hrportal.modules.payroll;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payslips")
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_run_id")
    private Long payrollRunId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pay_month", nullable = false)
    private Integer payMonth;

    @Column(name = "pay_year", nullable = false)
    private Integer payYear;

    @Column(name = "basic_salary", nullable = false)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(name = "overtime_pay", nullable = false)
    private BigDecimal overtimePay = BigDecimal.ZERO;

    @Column(name = "gross_salary", nullable = false)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "pf_deduction", nullable = false)
    private BigDecimal pfDeduction = BigDecimal.ZERO;

    @Column(name = "esi_deduction", nullable = false)
    private BigDecimal esiDeduction = BigDecimal.ZERO;

    @Column(name = "pt_deduction", nullable = false)
    private BigDecimal ptDeduction = BigDecimal.ZERO;

    @Column(name = "tds_deduction", nullable = false)
    private BigDecimal tdsDeduction = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false)
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_pay", nullable = false)
    private BigDecimal netPay = BigDecimal.ZERO;

    @Column(name = "lop_days", nullable = false)
    private BigDecimal lopDays = BigDecimal.ZERO;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    // ---- Customizable company / payslip fields (V15) ----
    // Populated when an admin generates a payslip from a request, so the
    // download reproduces exactly the company format the admin entered.

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_logo")
    private String companyLogo;

    @Column(name = "company_gstin")
    private String companyGstin;

    @Column(name = "company_address")
    private String companyAddress;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "designation")
    private String designation;

    @Column(name = "department")
    private String department;

    @Column(name = "pay_date")
    private java.time.LocalDate payDate;

    @Column(name = "working_days")
    private Integer workingDays;

    @Column(name = "performance_pay", nullable = false)
    private BigDecimal performancePay = BigDecimal.ZERO;

    @Column(name = "expenses_pay", nullable = false)
    private BigDecimal expensesPay = BigDecimal.ZERO;

    @Column(name = "salary_advance", nullable = false)
    private BigDecimal salaryAdvance = BigDecimal.ZERO;

    @Column(name = "health_insurance", nullable = false)
    private BigDecimal healthInsurance = BigDecimal.ZERO;

    @Column(name = "source", nullable = false)
    private String source = "BATCH"; // BATCH | REQUEST
}
