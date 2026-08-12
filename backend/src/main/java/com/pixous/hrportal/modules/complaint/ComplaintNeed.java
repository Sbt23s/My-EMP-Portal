package com.pixous.hrportal.modules.complaint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A complaint or a need/requirement raised by an employee or manager.
 * HR and Admin (USER_MANAGE) can see every entry and respond to it.
 */
@Getter
@Setter
@Entity
@Table(name = "complaints_needs")
public class ComplaintNeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 30)
    private String referenceCode;

    @Column(name = "raised_by", nullable = false)
    private Long raisedBy;

    /** The HR this was addressed to. Null means any HR may pick it up. */
    @Column(name = "requested_to")
    private Long requestedTo;

    /** COMPLAINT | NEED */
    @Column(nullable = false, length = 20)
    private String kind = "COMPLAINT";

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** LOW | MEDIUM | HIGH */
    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";

    /** OPEN | IN_REVIEW | RESOLVED | REJECTED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "hr_response", columnDefinition = "TEXT")
    private String hrResponse;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
