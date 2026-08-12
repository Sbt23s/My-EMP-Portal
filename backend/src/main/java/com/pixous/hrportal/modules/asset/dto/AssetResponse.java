package com.pixous.hrportal.modules.asset.dto;

import com.pixous.hrportal.modules.asset.Asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssetResponse(
        Long id, String assetCode, String category, String assetType, String brand,
        String model, String serialNumber, String registrationNo, LocalDate purchaseDate,
        BigDecimal purchaseCost, LocalDate warrantyExpiry, LocalDate amcExpiry,
        String status, Long siteId, Long assignedTo, String qrPath, BigDecimal depreciationRate,
        Integer quantity, LocalDateTime createdAt
) {
    public static AssetResponse from(Asset a) {
        return new AssetResponse(a.getId(), a.getAssetCode(), a.getCategory(), a.getAssetType(),
                a.getBrand(), a.getModel(), a.getSerialNumber(), a.getRegistrationNo(),
                a.getPurchaseDate(), a.getPurchaseCost(), a.getWarrantyExpiry(), a.getAmcExpiry(),
                a.getStatus(), a.getSiteId(), a.getAssignedTo(), a.getQrPath(), a.getDepreciationRate(),
                a.getQuantity(), a.getCreatedAt());
    }
}
