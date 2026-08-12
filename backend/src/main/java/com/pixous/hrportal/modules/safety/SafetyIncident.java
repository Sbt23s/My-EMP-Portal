package com.pixous.hrportal.modules.safety;

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
 * A safety incident reported by an employee.
 * Staff with REPORT_VIEW permission can see all entries, investigate, and resolve them.
 */
@Getter
@Setter
@Entity
@Table(name = "safety_incidents")
public class SafetyIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 30)
    private String referenceCode;

    @Column(name = "reported_by")
    private Long reportedBy;

    @Column(name = "site_id")
    private Long siteId;

    /** NEAR_MISS | MINOR_INJURY | MAJOR_INJURY | PROPERTY_DAMAGE | ENV_HAZARD */
    @Column(name = "incident_type", length = 40)
    private String incidentType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 120)
    private String zone;

    @Column(nullable = false)
    private boolean anonymous = false;

    /** OPEN | INVESTIGATING | RESOLVED | CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
