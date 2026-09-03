package com.pixous.hrportal.modules.config;

import com.pixous.hrportal.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Super Admin configuration API.
 *
 * Reads are open to any signed-in user, because the portal itself needs the
 * dropdown values to render its forms. Writes require CONFIG_MANAGE, which V109
 * grants to SUPER_ADMIN and COMPANY_ADMIN only — deliberately not to USER_MANAGE,
 * since managing people is not the same authority as changing how the
 * application behaves for everyone.
 *
 * Authorisation is declared here and enforced again in the service for the
 * platform-only settings. Hiding a control in the client is not a restriction.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    // ---------------- settings ----------------

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('CONFIG_MANAGE','USER_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    public ApiResponse<List<ConfigDTOs.SettingResponse>> settings(
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(configService.listSettings(category));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('CONFIG_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    public ApiResponse<ConfigDTOs.SettingResponse> updateSetting(
            @Valid @RequestBody ConfigDTOs.UpdateSettingRequest request) {
        return ApiResponse.ok(configService.updateSetting(request), "Setting saved");
    }

    // ---------------- dropdowns ----------------

    @GetMapping("/option-sets")
    public ApiResponse<List<ConfigDTOs.OptionSetSummary>> optionSets(
            @RequestParam(required = false) String module) {
        return ApiResponse.ok(configService.listOptionSets(module));
    }

    /**
     * Open to every signed-in user: the leave form needs its reasons. Defaults
     * to active values only, so a deactivated option disappears from new forms
     * while the configuration screens can still ask for all of them.
     */
    @GetMapping("/option-sets/{setCode}")
    public ApiResponse<ConfigDTOs.OptionSetResponse> optionSet(
            @PathVariable String setCode,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(configService.getOptionSet(setCode, activeOnly));
    }

    @PostMapping("/option-sets/{setCode}/options")
    @PreAuthorize("hasAuthority('CONFIG_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    public ApiResponse<ConfigDTOs.OptionResponse> saveOption(
            @PathVariable String setCode,
            @Valid @RequestBody ConfigDTOs.SaveOptionRequest request) {
        return ApiResponse.ok(configService.saveOption(setCode, request), "Option saved");
    }

    /** Deactivates rather than deletes — see {@link ConfigService#deactivateOption}. */
    @DeleteMapping("/option-sets/{setCode}/options/{optionCode}")
    @PreAuthorize("hasAuthority('CONFIG_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    public ApiResponse<Void> deactivateOption(@PathVariable String setCode,
                                              @PathVariable String optionCode) {
        configService.deactivateOption(setCode, optionCode);
        return ApiResponse.message("Option deactivated");
    }

    // ---------------- roles and summary ----------------

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('CONFIG_MANAGE','USER_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    public ApiResponse<List<ConfigDTOs.RoleResponse>> roles() {
        return ApiResponse.ok(configService.listRoles());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('CONFIG_MANAGE','USER_MANAGE') or hasRole('TECHNICAL_ADMIN')")
    public ApiResponse<ConfigDTOs.ConfigSummary> summary() {
        return ApiResponse.ok(configService.summary());
    }
}
