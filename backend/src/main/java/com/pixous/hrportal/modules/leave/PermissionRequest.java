package com.pixous.hrportal.modules.leave;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** An hours-wise "permission" request (short time-off within a work day). */
@Getter
@Setter
@Entity
@Table(name = "permission_requests")
public class PermissionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The approver this request is addressed to (manager/TL/HR). */
    @Column(name = "requested_to")
    private Long requestedTo;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "from_time", nullable = false, length = 5)
    private String fromTime;

    @Column(name = "to_time", nullable = false, length = 5)
    private String toTime;

    @Column(nullable = false)
    private BigDecimal hours = BigDecimal.ZERO;

    @Column(length = 500)
    private String reason;

    /** HIGH | MEDIUM | LOW — how urgent the request is. */
    @Column(length = 10, nullable = false)
    private String priority = "MEDIUM";

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_comment", length = 500)
    private String decisionComment;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
