package com.pixous.hrportal.modules.user.dto;

public record BankResponse(
        Long id,
        String bankName,
        String branchName,
        String accountNumber,
        String ifscCode,
        String accountHolderName,
        boolean primary
) {}
