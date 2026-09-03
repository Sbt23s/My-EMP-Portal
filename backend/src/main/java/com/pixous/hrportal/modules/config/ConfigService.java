package com.pixous.hrportal.modules.config;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.modules.user.Permission;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.RoleRepository;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the configuration store.
 *
 * Two rules are enforced here rather than in the controller or the client,
 * because this is the only path to the data:
 *
 *  1. A platform-only setting is refused to a company administrator. The client
 *     also renders it read-only, but that is a courtesy, not the control.
 *  2. A company never edits a platform default in place. Writing a value for a
 *     company creates or updates that company's own row, leaving the default
 *     intact for every other tenant.
 */
@Slf4j
@Service
public class ConfigService {

    private final AppSettingRepository settingRepository;
    private final ConfigOptionSetRepository optionSetRepository;
    private final ConfigOptionRepository optionRepository;
    private final RoleRepository roleRepository;

    public ConfigService(AppSettingRepository settingRepository,
                         ConfigOptionSetRepository optionSetRepository,
                         ConfigOptionRepository optionRepository,
                         RoleRepository roleRepository) {
        this.settingRepository = settingRepository;
        this.optionSetRepository = optionSetRepository;
        this.optionRepository = optionRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * True when the caller administers the platform rather than one tenant.
     * Platform-only settings answer to this and nothing else.
     */
    private boolean isPlatformAdmin() {
        return SecurityUtils.hasAuthority("ROLE_TECHNICAL_ADMIN")
                || SecurityUtils.hasAuthority("ROLE_SUPER_ADMIN");
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    /**
     * Every setting the caller can see, with a company override replacing the
     * platform default of the same key.
     */
    @Transactional(readOnly = true)
    public List<ConfigDTOs.SettingResponse> listSettings(String category) {
        Long companyId = SecurityUtils.currentCompanyId();
        List<AppSetting> rows = settingRepository.findVisibleTo(companyId);

        // Keyed so an override wins over the default; insertion order is not
        // relied upon, the company row is preferred explicitly.
        Map<String, AppSetting> resolved = new LinkedHashMap<>();
        for (AppSetting row : rows) {
            AppSetting existing = resolved.get(row.getSettingKey());
            if (existing == null || (existing.getCompanyId() == null && row.getCompanyId() != null)) {
                resolved.put(row.getSettingKey(), row);
            }
        }

        return resolved.values().stream()
                .filter(s -> category == null || category.isBlank() || category.equalsIgnoreCase(s.getCategory()))
                .sorted(Comparator.comparing(AppSetting::getCategory)
                        .thenComparing(AppSetting::getSettingKey))
                .map(this::toSettingResponse)
                .toList();
    }

    private ConfigDTOs.SettingResponse toSettingResponse(AppSetting s) {
        return new ConfigDTOs.SettingResponse(
                s.getId(), s.getSettingKey(), s.getSettingValue(), s.getValueType(),
                s.getCategory(), s.getLabel(), s.getDescription(),
                s.isPlatformOnly(), s.isEditable(), s.getCompanyId() == null);
    }

    /**
     * Writes a value for the caller's scope. A platform administrator with no
     * company edits the default; a company administrator gets their own row.
     */
    @Transactional
    public ConfigDTOs.SettingResponse updateSetting(ConfigDTOs.UpdateSettingRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();

        AppSetting definition = settingRepository.findPlatformDefault(request.key())
                .or(() -> settingRepository.findBySettingKeyAndCompanyId(request.key(), companyId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Unknown setting: " + request.key()));

        if (!definition.isEditable()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "This setting is fixed and cannot be changed.");
        }
        if (definition.isPlatformOnly() && !isPlatformAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "This setting is controlled by the platform administrator.");
        }

        String value = normalise(definition.getValueType(), request.value());

        // A platform admin without a company edits the default row itself.
        boolean editDefault = companyId == null && isPlatformAdmin();
        AppSetting target;
        if (editDefault) {
            target = definition;
        } else {
            target = settingRepository.findBySettingKeyAndCompanyId(request.key(), companyId)
                    .orElseGet(() -> {
                        AppSetting fresh = new AppSetting();
                        fresh.setCompanyId(companyId);
                        fresh.setSettingKey(definition.getSettingKey());
                        fresh.setValueType(definition.getValueType());
                        fresh.setCategory(definition.getCategory());
                        fresh.setLabel(definition.getLabel());
                        fresh.setDescription(definition.getDescription());
                        fresh.setPlatformOnly(definition.isPlatformOnly());
                        fresh.setEditable(definition.isEditable());
                        return fresh;
                    });
        }

        target.setSettingValue(value);
        AppSetting saved = settingRepository.save(target);
        log.info("Setting {} updated for company {}", saved.getSettingKey(), companyId);
        return toSettingResponse(saved);
    }

    /**
     * Rejects a value that does not fit its declared type, so a malformed number
     * is refused at the point of entry rather than when something later reads it.
     */
    private String normalise(String valueType, String raw) {
        String value = raw == null ? "" : raw.trim();
        String type = valueType == null ? "STRING" : valueType.toUpperCase();
        try {
            switch (type) {
                case "INT" -> {
                    return String.valueOf(Long.parseLong(value));
                }
                case "DECIMAL" -> {
                    return new BigDecimal(value).toPlainString();
                }
                case "BOOLEAN" -> {
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                        return value.toLowerCase();
                    }
                    throw new NumberFormatException("not a boolean");
                }
                default -> {
                    return value;
                }
            }
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Value must be of type " + type + ".");
        }
    }

    // ------------------------------------------------------------------
    // Dropdowns
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConfigDTOs.OptionSetSummary> listOptionSets(String module) {
        Long companyId = SecurityUtils.currentCompanyId();
        return dedupeSets(optionSetRepository.findVisibleTo(companyId)).stream()
                .filter(s -> module == null || module.isBlank() || module.equalsIgnoreCase(s.getModule()))
                .map(s -> new ConfigDTOs.OptionSetSummary(
                        s.getId(), s.getSetCode(), s.getName(), s.getModule(),
                        s.isSystemSet(), s.getCompanyId() == null,
                        optionRepository.countByOptionSetId(s.getId())))
                .toList();
    }

    /** One row per set code, a company's own set preferred over the default. */
    private List<ConfigOptionSet> dedupeSets(List<ConfigOptionSet> rows) {
        Map<String, ConfigOptionSet> byCode = new LinkedHashMap<>();
        for (ConfigOptionSet row : rows) {
            ConfigOptionSet existing = byCode.get(row.getSetCode());
            if (existing == null || (existing.getCompanyId() == null && row.getCompanyId() != null)) {
                byCode.put(row.getSetCode(), row);
            }
        }
        return new ArrayList<>(byCode.values());
    }

    @Transactional(readOnly = true)
    public ConfigDTOs.OptionSetResponse getOptionSet(String setCode, boolean activeOnly) {
        ConfigOptionSet set = resolveSet(setCode);
        List<ConfigOption> options = activeOnly
                ? optionRepository.findByOptionSetIdAndActiveTrueOrderBySortOrderAscLabelAsc(set.getId())
                : optionRepository.findByOptionSetIdOrderBySortOrderAscLabelAsc(set.getId());
        return new ConfigDTOs.OptionSetResponse(
                set.getId(), set.getSetCode(), set.getName(), set.getModule(),
                set.getDescription(), set.isSystemSet(), set.getCompanyId() == null,
                options.stream().map(this::toOptionResponse).toList());
    }

    private ConfigOptionSet resolveSet(String setCode) {
        Long companyId = SecurityUtils.currentCompanyId();
        List<ConfigOptionSet> found = optionSetRepository.findByCodeForCompany(setCode, companyId);
        if (found.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Unknown option set: " + setCode);
        }
        return found.get(0);
    }

    private ConfigDTOs.OptionResponse toOptionResponse(ConfigOption o) {
        return new ConfigDTOs.OptionResponse(o.getId(), o.getOptionCode(), o.getLabel(),
                o.getSortOrder(), o.isActive(), o.isDefault(), o.getMetadata());
    }

    /**
     * Adds or updates one value in a set, addressed by its code so the caller
     * does not have to know whether the row exists yet.
     */
    @Transactional
    public ConfigDTOs.OptionResponse saveOption(String setCode, ConfigDTOs.SaveOptionRequest request) {
        ConfigOptionSet set = resolveSet(setCode);

        ConfigOption option = optionRepository
                .findByOptionSetIdAndOptionCode(set.getId(), request.code())
                .orElseGet(() -> {
                    ConfigOption fresh = new ConfigOption();
                    fresh.setOptionSetId(set.getId());
                    fresh.setOptionCode(request.code());
                    return fresh;
                });

        option.setLabel(request.label());
        if (request.sortOrder() != null) option.setSortOrder(request.sortOrder());
        if (request.active() != null) option.setActive(request.active());
        if (request.metadata() != null) option.setMetadata(request.metadata());

        if (Boolean.TRUE.equals(request.isDefault())) {
            // One default per set, so clear the others rather than leaving two.
            optionRepository.findByOptionSetIdOrderBySortOrderAscLabelAsc(set.getId())
                    .forEach(other -> {
                        if (other.isDefault() && !other.getId().equals(option.getId())) {
                            other.setDefault(false);
                            optionRepository.save(other);
                        }
                    });
            option.setDefault(true);
        } else if (request.isDefault() != null) {
            option.setDefault(false);
        }

        return toOptionResponse(optionRepository.save(option));
    }

    /**
     * Deactivates a value instead of deleting it. Records already referencing it
     * keep their meaning; new records can no longer choose it. A hard delete
     * would leave existing rows pointing at a code with no label.
     */
    @Transactional
    public void deactivateOption(String setCode, String optionCode) {
        ConfigOptionSet set = resolveSet(setCode);
        ConfigOption option = optionRepository
                .findByOptionSetIdAndOptionCode(set.getId(), optionCode)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Unknown option: " + optionCode));
        option.setActive(false);
        option.setDefault(false);
        optionRepository.save(option);
    }

    // ------------------------------------------------------------------
    // Roles and permissions, read-only here
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConfigDTOs.RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(r -> new ConfigDTOs.RoleResponse(
                        r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getCompanyId(),
                        r.getPermissions().stream().map(Permission::getCode).sorted().toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConfigDTOs.ConfigSummary summary() {
        Long companyId = SecurityUtils.currentCompanyId();
        List<AppSetting> settings = settingRepository.findVisibleTo(companyId);
        List<ConfigOptionSet> sets = dedupeSets(optionSetRepository.findVisibleTo(companyId));
        long optionCount = sets.stream()
                .mapToLong(s -> optionRepository.countByOptionSetId(s.getId()))
                .sum();
        List<Role> roles = roleRepository.findAll();
        long permissionCount = roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .count();

        return new ConfigDTOs.ConfigSummary(
                settings.stream().map(AppSetting::getSettingKey).distinct().count(),
                sets.size(),
                optionCount,
                roles.size(),
                permissionCount,
                settings.stream().map(AppSetting::getCategory).distinct().sorted().toList(),
                sets.stream().map(ConfigOptionSet::getModule)
                        .filter(m -> m != null && !m.isBlank()).distinct().sorted().toList());
    }

    /**
     * Reads one setting for application code, falling back to the platform
     * default and then to the supplied value. Used by features that need a
     * configured number without depending on the screens being visited.
     */
    @Transactional(readOnly = true)
    public int intSetting(String key, Long companyId, int fallback) {
        Optional<AppSetting> row = settingRepository.findBySettingKeyAndCompanyId(key, companyId)
                .or(() -> settingRepository.findPlatformDefault(key));
        try {
            return row.map(AppSetting::getSettingValue).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException e) {
            log.warn("Setting {} is not an integer; using {}", key, fallback);
            return fallback;
        }
    }
}
