package com.pixous.hrportal.modules.approvalconfig;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One tick in the administrator's visibility grid: this role sees this module.
 *
 * <p>The other half of {@link ApprovalRecipientConfig}. That one says who a
 * request may be addressed to; this says which modules a role sees at all.
 * See V152 for why both are needed and why neither grants anything.
 */
@Entity
@Table(name = "role_module_visibility")
@Getter
@Setter
public class RoleModuleVisibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    /** A role code, or the pseudo-role CTO. */
    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    /** LEAVE | PERMISSION | WFH | APPROVALS | POLICIES */
    @Column(name = "module_code", nullable = false, length = 40)
    private String moduleCode;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
