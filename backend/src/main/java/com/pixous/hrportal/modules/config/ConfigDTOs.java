package com.pixous.hrportal.modules.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request and response shapes for the configuration screens. */
public final class ConfigDTOs {

    private ConfigDTOs() {
    }

    public record SettingResponse(
            Long id,
            String key,
            String value,
            String valueType,
            String category,
            String label,
            String description,
            boolean platformOnly,
            boolean editable,
            /** True when this value is the platform default, not a company override. */
            boolean inherited
    ) {}

    public record UpdateSettingRequest(
            @NotBlank @Size(max = 120) String key,
            @Size(max = 5000) String value
    ) {}

    public record OptionResponse(
            Long id,
            String code,
            String label,
            int sortOrder,
            boolean active,
            boolean isDefault,
            String metadata
    ) {}

    public record OptionSetResponse(
            Long id,
            String code,
            String name,
            String module,
            String description,
            boolean systemSet,
            boolean inherited,
            List<OptionResponse> options
    ) {}

    public record OptionSetSummary(
            Long id,
            String code,
            String name,
            String module,
            boolean systemSet,
            boolean inherited,
            long optionCount
    ) {}

    public record SaveOptionRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 200) String label,
            Integer sortOrder,
            Boolean active,
            Boolean isDefault,
            @Size(max = 2000) String metadata
    ) {}

    public record RoleResponse(
            Long id,
            String code,
            String name,
            String description,
            Long companyId,
            List<String> permissions
    ) {}

    public record PermissionResponse(Long id, String code, String name) {}

    /** What the Super Admin dashboard shows without needing several calls. */
    public record ConfigSummary(
            long settingCount,
            long optionSetCount,
            long optionCount,
            long roleCount,
            long permissionCount,
            List<String> categories,
            List<String> modules
    ) {}
}
