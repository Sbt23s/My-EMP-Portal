package com.pixous.hrportal.modules.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnRequest(
        @NotBlank String condition   // GOOD | DAMAGED | LOST
) {}
