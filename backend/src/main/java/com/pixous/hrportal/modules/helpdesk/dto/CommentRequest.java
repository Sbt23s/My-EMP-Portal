package com.pixous.hrportal.modules.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(
        @NotBlank String comment,
        String attachmentPath
) {}
