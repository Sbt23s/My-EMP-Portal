package com.pixous.hrportal.modules.asset;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.asset.dto.*;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetAllocationRepository allocationRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    @Transactional(readOnly = true)
    public PageResponse<AssetResponse> list(String status, String category, int page, int size) {
        var pageable = PageRequest.of(page, size);
        Page<Asset> result;
        if (status != null && !status.isBlank()) {
            result = assetRepository.findByStatus(status, pageable);
        } else if (category != null && !category.isBlank()) {
            result = assetRepository.findByCategory(category, pageable);
        } else {
            result = assetRepository.findAll(pageable);
        }
        return PageResponse.from(result.map(AssetResponse::from));
    }

    @Transactional(readOnly = true)
    public AssetResponse get(Long id) {
        return AssetResponse.from(find(id));
    }

    @Transactional
    public AssetResponse create(AssetRequest req) {
        Asset a = new Asset();
        a.setAssetCode(generateAssetCode(req.category()));
        a.setCategory(req.category());
        a.setAssetType(req.assetType());
        a.setBrand(req.brand());
        a.setModel(req.model());
        a.setSerialNumber(req.serialNumber());
        a.setRegistrationNo(req.registrationNo());
        a.setPurchaseDate(req.purchaseDate());
        a.setPurchaseCost(req.purchaseCost());
        a.setWarrantyExpiry(req.warrantyExpiry());
        a.setAmcExpiry(req.amcExpiry());
        a.setSiteId(req.siteId());
        a.setDepreciationRate(req.depreciationRate());
        int qty = req.quantity() != null ? req.quantity() : 1;
        a.setQuantity(qty);
        a.setStatus(qty <= 0 ? "OUT_OF_STOCK" : "IN_STOCK");
        Asset saved = assetRepository.save(a);
        // Generate + attach QR tag
        saved.setQrPath(qrCodeService.storeForAsset(saved.getAssetCode()));
        return AssetResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(Long id) {
        Asset a = find(id);
        return qrCodeService.pngBytes("ASSET:" + a.getAssetCode(), 320);
    }

    @Transactional
    public AssetResponse allocate(Long assetId, AllocateRequest req) {
        Asset a = find(assetId);
        if ("OUT_OF_STOCK".equals(a.getStatus()) || a.getQuantity() <= 0) {
            throw ApiException.business("Asset is out of stock");
        }
        if ("RETIRED".equals(a.getStatus()) || "LOST".equals(a.getStatus())) {
            throw ApiException.business("Cannot allocate a " + a.getStatus().toLowerCase() + " asset");
        }
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> ApiException.notFound("User"));

        AssetAllocation alloc = new AssetAllocation();
        alloc.setAssetId(assetId);
        alloc.setUserId(req.userId());
        alloc.setAllocatedAt(LocalDateTime.now());
        allocationRepository.save(alloc);

        a.setQuantity(a.getQuantity() - 1);
        if (a.getQuantity() <= 0) {
            a.setStatus("OUT_OF_STOCK");
        }
        a.setAssignedTo(req.userId());
        a.setUpdatedAt(LocalDateTime.now());

        String detail = a.getAssetType() + " (" + a.getAssetCode() + ") has been assigned to you. "
                + "Please acknowledge receipt.";
        notificationService.createAndPush(req.userId(), "Asset assigned", detail, "ASSET", "/assets");
        userRepository.findById(req.userId())
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + detail));
        return AssetResponse.from(a);
    }

    @Transactional
    public AssetResponse acknowledge(Long userId, Long assetId) {
        Asset a = find(assetId);
        AssetAllocation alloc = allocationRepository
                .findByAssetIdAndReturnedAtIsNull(assetId)
                .orElseThrow(() -> ApiException.business("No active allocation for this asset"));
        if (!alloc.getUserId().equals(userId)) {
            throw ApiException.business("Only the assignee can acknowledge this asset");
        }
        alloc.setAcknowledged(true);
        alloc.setAcknowledgedAt(LocalDateTime.now());
        return AssetResponse.from(a);
    }

    @Transactional
    public AssetResponse returnAsset(Long assetId, ReturnRequest req) {
        Asset a = find(assetId);
        AssetAllocation alloc = allocationRepository
                .findByAssetIdAndReturnedAtIsNull(assetId)
                .orElseThrow(() -> ApiException.business("Asset is not currently allocated"));
        alloc.setReturnedAt(LocalDateTime.now());
        alloc.setReturnCondition(req.condition());

        String condition = req.condition() == null ? "GOOD" : req.condition().toUpperCase();
        a.setQuantity(a.getQuantity() + 1);
        a.setStatus(condition.equals("LOST") ? "LOST"
                : condition.equals("DAMAGED") ? "UNDER_REPAIR" : "IN_STOCK");
        a.setAssignedTo(null);
        a.setUpdatedAt(LocalDateTime.now());
        return AssetResponse.from(a);
    }

    @Transactional
    public void delete(Long id) {
        Asset asset = find(id);
        // Remove allocation history first so the FK to this asset doesn't block deletion.
        allocationRepository.deleteByAssetId(id);
        assetRepository.delete(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> myAssets(Long userId) {
        List<Long> assetIds = allocationRepository.findByUserIdAndReturnedAtIsNull(userId).stream()
                .map(AssetAllocation::getAssetId).toList();
        return assetRepository.findAllById(assetIds).stream()
                .map(AssetResponse::from).toList();
    }

    // ---- helpers ----

    @Transactional(readOnly = true)
    public AssetResponse getByCode(String code) {
        Asset a = assetRepository.findByAssetCode(code)
                .orElseThrow(() -> ApiException.notFound("Asset"));
        return AssetResponse.from(a);
    }

    private Asset find(Long id) {
        return assetRepository.findById(id).orElseThrow(() -> ApiException.notFound("Asset"));
    }

    private String generateAssetCode(String category) {
        String prefix = switch (category == null ? "IT" : category.toUpperCase()) {
            case "INFRA" -> "INF";
            case "MACHINERY" -> "MCH";
            default -> "AST";
        };
        long count = assetRepository.count() + 1;
        return prefix + "-" + Year.now().getValue() + "-" + String.format("%05d", count);
    }
}
