package com.pixous.hrportal.modules.wfh;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A request to work from home for a day or a range of days.
 *
 * <p>Deliberately not a kind of leave. A WFH day is a working day: the person
 * is at work, payroll pays them, attendance expects them, and no balance is
 * consumed. Modelled as leave it would deduct from an allocation, show in the
 * leave calendar as an absence, and count against the quarterly caps — all
 * wrong for somebody who is working.
 *
 * <p>The shape mirrors {@code LeaveRequest} on purpose, so the routing, the
 * statuses and the screens read the same way to anybody who knows this system.
 */
@Getter
@Setter
@Entity
@Table(name = "wfh_requests")
public class WfhRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "working_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal workingDays = BigDecimal.ZERO;

    @Column(length = 1000)
    private String reason;

    @Column(length = 1000)
    private String remarks;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "requested_to")
    private Long requestedTo;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Still waiting on somebody. */
    public boolean isPending() {
        return PENDING.equals(status);
    }

    /**
     * Whether an approved request has already run its course.
     *
     * <p>Derived rather than stored: "completed" is the past tense of
     * approved, and storing it would need a job every night that would leave
     * the wrong answer on any day it failed to run.
     */
    public boolean isCompleted(LocalDate today) {
        return APPROVED.equals(status) && toDate.isBefore(today);
    }

    /** Approved and covering today: the person is at home right now. */
    public boolean isActiveOn(LocalDate day) {
        return APPROVED.equals(status)
                && !fromDate.isAfter(day)
                && !toDate.isBefore(day);
    }
}
