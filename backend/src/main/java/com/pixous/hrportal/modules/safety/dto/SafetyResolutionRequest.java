package com.pixous.hrportal.modules.safety.dto;

import jakarta.validation.constraints.NotBlank;

/** Staff action on a safety incident: set a status and optionally add resolution notes. */
public record SafetyResolutionRequest(
        @NotBlank(message = "Status is required")
        String status,          // OPEN | INVESTIGATING | RESOLVED | CLOSED
        String resolutionNotes  // optional notes about the resolution
) {}
