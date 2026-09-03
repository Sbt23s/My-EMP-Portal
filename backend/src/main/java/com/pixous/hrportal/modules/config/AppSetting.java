package com.pixous.hrportal.modules.config;

import com.pixous.hrportal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One configurable value.
 *
 * {@code companyId} null means the platform default; a row with a company id
 * overrides it for that tenant only. Resolution is handled in
 * {@link ConfigService}, not here, so the override rule lives in one place.
 */
@Getter
@Setter
@Entity
@Table(name = "app_settings")
public class AppSetting extends BaseEntity {

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "setting_key", nullable = false, length = 120)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    /** STRING, INT, BOOLEAN, DECIMAL, JSON — validated on write. */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType = "STRING";

    @Column(nullable = false, length = 60)
    private String category = "GENERAL";

    @Column(length = 200)
    private String label;

    @Column(length = 500)
    private String description;

    /**
     * Only the platform owner may change this. A company administrator sees it
     * and is refused on write — checked in the service, because hiding a field
     * in the client is not a restriction.
     */
    @Column(name = "platform_only", nullable = false)
    private boolean platformOnly = false;

    @Column(nullable = false)
    private boolean editable = true;
}
