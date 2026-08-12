package com.pixous.hrportal.modules.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetRequest(
        @NotBlank String category,
        @NotBlank String assetType,
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String serialNumber,
        String registrationNo,
        LocalDate purchaseDate,
        BigDecimal purchaseCost,
        LocalDate warrantyExpiry,
        LocalDate amcExpiry,
        Long siteId,
        BigDecimal depreciationRate,
        @NotNull @Min(1) Integer quantity
) {}
