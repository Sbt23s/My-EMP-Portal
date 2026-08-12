package com.pixous.hrportal.modules.asset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_code", nullable = false, unique = true, length = 40)
    private String assetCode;

    @Column(nullable = false, length = 30)
    private String category = "IT";

    @Column(name = "asset_type", length = 60)
    private String assetType;

    @Column(length = 80)
    private String brand;

    @Column(length = 80)
    private String model;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(name = "registration_no", length = 60)
    private String registrationNo;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost")
    private BigDecimal purchaseCost;

    @Column(name = "warranty_expiry")
    private LocalDate warrantyExpiry;

    @Column(name = "amc_expiry")
    private LocalDate amcExpiry;

    @Column(nullable = false, length = 20)
    private String status = "IN_STOCK";

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "qr_path", length = 255)
    private String qrPath;

    @Column(name = "depreciation_rate")
    private BigDecimal depreciationRate;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
