package com.pixous.hrportal.modules.discipline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A disciplinary record: HR raises one about an employee, the CTO reviews it.
 *
 * <p>Its own table rather than a kind of complaint. A complaint is raised BY
 * somebody about something and answered by HR; this is raised ABOUT somebody
 * by HR, carries a severity and an action taken, and is reviewed by the CTO.
 * The two share a shape and nothing else, and folding one into the other would
 * mean every query in both having to say which it meant.
 */
@Getter
@Setter
@Entity
@Table(name = "discipline_records")
public class DisciplineRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DSP-2026-00001. Generated, unique, and what people quote to each other. */
    @Column(name = "reference_code", nullable = false, unique = true, length = 30)
    private String referenceCode;

    /** The person the record is about. */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** The HR or admin who raised it. */
    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "discipline_type", nullable = false, length = 60)
    private String disciplineType;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "action_taken", length = 60)
    private String actionTaken;

    /**
     * Comma-separated storage paths, the shape tickets and leave already use,
     * so /api/files serves them without a second mechanism being invented.
     */
    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(name = "employee_response", columnDefinition = "TEXT")
    private String employeeResponse;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /** The CTO's warning message, which the employee is shown. */
    @Column(name = "cto_remarks", columnDefinition = "TEXT")
    private String ctoRemarks;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** OPEN | UNDER_REVIEW | RESOLVED | CLOSED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
