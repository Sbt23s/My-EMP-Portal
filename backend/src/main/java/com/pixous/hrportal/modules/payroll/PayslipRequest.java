package com.pixous.hrportal.modules.payroll;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A request from an employee / HR / manager asking Admin to generate their
 * payslip for a given month. Admin approves it (filling the customizable
 * payslip form) which creates the {@link Payslip} and links it here; only
 * the requester can then download that payslip.
 */
@Getter
@Setter
@Entity
@Table(name = "payslip_requests")
public class PayslipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pay_month", nullable = false)
    private Integer payMonth;

    @Column(name = "pay_year", nullable = false)
    private Integer payYear;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // PENDING | APPROVED | REJECTED

    @Column(name = "payslip_id")
    private Long payslipId;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
