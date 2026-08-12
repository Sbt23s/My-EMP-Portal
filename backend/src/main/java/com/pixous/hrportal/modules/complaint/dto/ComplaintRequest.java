package com.pixous.hrportal.modules.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload an employee/manager submits to raise a complaint or need. */
public record ComplaintRequest(
        /** COMPLAINT | NEED — defaults to COMPLAINT when null. */
        String kind,
        String category,

        @NotBlank(message = "Subject is required")
        @Size(max = 200)
        String subject,

        @NotBlank(message = "Please describe your complaint or need")
        String description,

        /** LOW | MEDIUM | HIGH — defaults to MEDIUM when null. */
        String priority,

        /** The HR this is addressed to. Null lets any HR pick it up. */
        Long requestedTo
) {}
