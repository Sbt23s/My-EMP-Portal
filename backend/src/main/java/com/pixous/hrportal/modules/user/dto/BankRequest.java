package com.pixous.hrportal.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BankRequest(
        @NotBlank String bankName,
        String branchName,
        @NotBlank @Pattern(regexp = "\\d{6,20}", message = "Invalid account number") String accountNumber,
        @NotBlank @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}", message = "Invalid IFSC code") String ifscCode,
        @NotBlank String accountHolderName,
        Boolean primary
) {}
