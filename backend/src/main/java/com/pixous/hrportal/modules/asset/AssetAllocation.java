package com.pixous.hrportal.modules.asset;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "asset_allocations")
public class AssetAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "allocated_at", nullable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean acknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "return_condition", length = 20)
    private String returnCondition;
}
