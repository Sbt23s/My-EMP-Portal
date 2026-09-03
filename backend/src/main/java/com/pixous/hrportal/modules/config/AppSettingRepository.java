package com.pixous.hrportal.modules.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Scoping is explicit in every query rather than left to the Hibernate tenant
 * filter. These rows are read during login and from scheduled jobs, where no
 * security context exists and the filter is inactive — a configuration read
 * that silently returned another tenant's value would be worse than one that
 * returned nothing.
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    /** Platform defaults plus this company's overrides, in one pass. */
    @Query("SELECT s FROM AppSetting s WHERE s.companyId IS NULL OR s.companyId = :companyId")
    List<AppSetting> findVisibleTo(@Param("companyId") Long companyId);

    @Query("SELECT s FROM AppSetting s WHERE s.companyId IS NULL")
    List<AppSetting> findPlatformDefaults();

    Optional<AppSetting> findBySettingKeyAndCompanyId(String settingKey, Long companyId);

    @Query("SELECT s FROM AppSetting s WHERE s.settingKey = :key AND s.companyId IS NULL")
    Optional<AppSetting> findPlatformDefault(@Param("key") String key);
}
