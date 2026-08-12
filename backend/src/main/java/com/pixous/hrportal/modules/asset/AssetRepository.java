package com.pixous.hrportal.modules.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Page<Asset> findByStatus(String status, Pageable pageable);
    Page<Asset> findByCategory(String category, Pageable pageable);
    List<Asset> findByAssignedTo(Long userId);
    long countByStatus(String status);
    java.util.Optional<Asset> findByAssetCode(String assetCode);
}
