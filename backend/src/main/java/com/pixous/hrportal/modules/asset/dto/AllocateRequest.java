package com.pixous.hrportal.modules.asset.dto;

import jakarta.validation.constraints.NotNull;

public record AllocateRequest(
        @NotNull Long userId
) {}
