package com.pixous.hrportal.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneValidateRequest(@NotBlank String phone) {}
