package com.pixous.hrportal.modules.approvalconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRecipientConfigRepository
        extends JpaRepository<ApprovalRecipientConfig, Long> {

    /**
     * Everything configured for one module.
     *
     * <p>Both the company's own rows and the ones with no company: a
     * single-tenant install writes NULL there, and a reader that matched only
     * on the id would find nothing and — because absent means unrestricted —
     * silently ignore the configuration somebody had just saved.
     */
    List<ApprovalRecipientConfig> findByModuleCode(String moduleCode);

    List<ApprovalRecipientConfig> findAllByOrderByModuleCodeAscRoleCodeAsc();
}
