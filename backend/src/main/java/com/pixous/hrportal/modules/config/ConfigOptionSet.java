package com.pixous.hrportal.modules.config;

import com.pixous.hrportal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A named dropdown — "leave.request_reason", "expense.category". */
@Getter
@Setter
@Entity
@Table(name = "config_option_sets")
public class ConfigOptionSet extends BaseEntity {

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "set_code", nullable = false, length = 80)
    private String setCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 60)
    private String module;

    @Column(length = 500)
    private String description;

    /**
     * The application reads this set by code, so the set itself must keep
     * existing. Its values may still be added to, reordered and relabelled.
     */
    @Column(name = "system_set", nullable = false)
    private boolean systemSet = false;
}
