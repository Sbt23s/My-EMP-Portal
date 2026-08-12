package com.pixous.hrportal.modules.asset;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.asset.dto.*;
import com.pixous.hrportal.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public PageResponse<AssetResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, category, page, size);
    }

    @GetMapping("/my-assets")
    public ApiResponse<List<AssetResponse>> myAssets() {
        return ApiResponse.ok(service.myAssets(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AssetResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public ApiResponse<AssetResponse> create(@Valid @RequestBody AssetRequest req) {
        return ApiResponse.ok(service.create(req), "Asset registered");
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<ByteArrayResource> qr(@PathVariable Long id) {
        byte[] png = service.qrPng(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new ByteArrayResource(png));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null, "Asset deleted");
    }

    @PostMapping("/{id}/allocate")
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public ApiResponse<AssetResponse> allocate(
            @PathVariable Long id, @Valid @RequestBody AllocateRequest req) {
        return ApiResponse.ok(service.allocate(id, req), "Asset allocated");
    }

    @PostMapping("/{id}/acknowledge")
    public ApiResponse<AssetResponse> acknowledge(@PathVariable Long id) {
        return ApiResponse.ok(service.acknowledge(SecurityUtils.currentUserId(), id),
                "Receipt acknowledged");
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public ApiResponse<AssetResponse> returnAsset(
            @PathVariable Long id, @Valid @RequestBody ReturnRequest req) {
        return ApiResponse.ok(service.returnAsset(id, req), "Asset returned");
    }

    @GetMapping("/lookup")
    public ApiResponse<AssetResponse> getByCode(@RequestParam String code) {
        return ApiResponse.ok(service.getByCode(code));
    }
}
