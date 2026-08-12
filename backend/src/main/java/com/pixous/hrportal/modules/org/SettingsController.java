package com.pixous.hrportal.modules.org;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.config.CacheConfig;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings", description = "Global system settings")
public class SettingsController {

    private final SystemSettingRepository repository;

    public SettingsController(SystemSettingRepository repository) {
        this.repository = repository;
    }

    /**
     * Cached. Twenty-one rows that change when an admin edits them, fetched by the
     * web app on every reload; the eviction below is what keeps it honest, and the
     * fifteen-minute TTL is only the backstop.
     */
    @GetMapping
    // Keyed by company. Under the old key of "all", the first tenant to load the
    // page put its settings in the cache and every other tenant read them back.
    @Cacheable(cacheNames = CacheConfig.SETTINGS,
               key = "T(com.pixous.hrportal.security.SecurityUtils).currentCompanyId() + ':all'")
    @Operation(summary = "Get all settings as key-value map")
    public ApiResponse<Map<String, String>> getAllSettings() {
        Map<String, String> map = repository.findAll().stream()
                .collect(Collectors.toMap(SystemSetting::getKey, SystemSetting::getValue));
        return ApiResponse.ok(map);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @CacheEvict(cacheNames = CacheConfig.SETTINGS, allEntries = true)
    @Operation(summary = "Update settings")
    public ApiResponse<Void> updateSettings(@RequestBody Map<String, String> settings) {
        settings.forEach((k, v) -> {
            repository.findById(k).ifPresent(s -> {
                s.setValue(v);
                repository.save(s);
            });
        });
        return ApiResponse.message("Settings updated");
    }
}
