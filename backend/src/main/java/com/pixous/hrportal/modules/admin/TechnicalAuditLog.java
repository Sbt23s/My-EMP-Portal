package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "technical_audit_logs")
public class TechnicalAuditLog extends BaseEntity {

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_username")
    private String adminUsername;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "ip_address")
    private String ipAddress;
}
