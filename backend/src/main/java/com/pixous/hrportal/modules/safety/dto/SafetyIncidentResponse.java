package com.pixous.hrportal.modules.safety.dto;

import com.pixous.hrportal.modules.safety.SafetyIncident;

import java.time.LocalDateTime;

/** View returned to clients. Includes reporter/resolver display names. */
public record SafetyIncidentResponse(
        Long id,
        String referenceCode,
        Long reportedBy,
        String reportedByName,
        Long siteId,
        String incidentType,
        String description,
        String zone,
        boolean anonymous,
        String status,
        String severity,
        LocalDateTime occurredAt,
        Long resolvedBy,
        String resolvedByName,
        String resolutionNotes,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
    public static SafetyIncidentResponse from(SafetyIncident s, String reportedByName, String resolvedByName) {
        return new SafetyIncidentResponse(
                s.getId(),
                s.getReferenceCode(),
                s.getReportedBy(),
                reportedByName,
                s.getSiteId(),
                s.getIncidentType(),
                s.getDescription(),
                s.getZone(),
                s.isAnonymous(),
                s.getStatus(),
                s.getSeverity(),
                s.getOccurredAt(),
                s.getResolvedBy(),
                resolvedByName,
                s.getResolutionNotes(),
                s.getResolvedAt(),
                s.getCreatedAt()
        );
    }
}
