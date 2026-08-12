package com.pixous.hrportal.modules.safety.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload an employee submits to report a safety incident. */
public record SafetyIncidentRequest(
        /** NEAR_MISS | MINOR_INJURY | MAJOR_INJURY | PROPERTY_DAMAGE | ENV_HAZARD */
        String incidentType,

        @NotBlank(message = "Please describe the incident")
        String description,

        String zone,
        boolean anonymous,

        /** ISO date-time string (optional). When the incident occurred. */
        String occurredAt,

        /** LOW | MEDIUM | HIGH | CRITICAL — defaults to MEDIUM when null. */
        String severity
) {}
