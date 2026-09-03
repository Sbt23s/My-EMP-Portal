package com.pixous.hrportal.modules.config;

import com.pixous.hrportal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One value inside a {@link ConfigOptionSet}. */
@Getter
@Setter
@Entity
@Table(name = "config_options")
public class ConfigOption extends BaseEntity {

    @Column(name = "option_set_id", nullable = false)
    private Long optionSetId;

    @Column(name = "option_code", nullable = false, length = 80)
    private String optionCode;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /**
     * Deactivating rather than deleting: rows already referencing this option
     * keep their label, while new records stop being able to choose it.
     */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    /** Free-form extras for options carrying data — a colour, a day count. */
    @Column(columnDefinition = "TEXT")
    private String metadata;
}
