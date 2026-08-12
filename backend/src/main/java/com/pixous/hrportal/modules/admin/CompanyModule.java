package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.BaseEntity;
import com.pixous.hrportal.modules.org.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "company_modules")
public class CompanyModule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode; // e.g. "attendance", "chat", "payroll"

    @Column(nullable = false)
    private boolean enabled = false;

    // JSON string storing feature flags for this specific module
    @Column(name = "feature_flags", columnDefinition = "TEXT")
    private String featureFlags;
}
