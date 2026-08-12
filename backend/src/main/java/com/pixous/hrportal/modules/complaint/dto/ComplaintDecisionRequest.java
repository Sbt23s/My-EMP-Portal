package com.pixous.hrportal.modules.complaint.dto;

import jakarta.validation.constraints.NotBlank;

/** HR/Admin action on a complaint/need: set a status and optionally a reply. */
public record ComplaintDecisionRequest(
        @NotBlank(message = "Status is required")
        String status,          // OPEN | IN_REVIEW | RESOLVED | REJECTED
        String response         // optional message back to the submitter
) {}
