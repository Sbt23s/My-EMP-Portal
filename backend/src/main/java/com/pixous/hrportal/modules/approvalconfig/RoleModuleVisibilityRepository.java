package com.pixous.hrportal.modules.approvalconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RoleModuleVisibilityRepository
        extends JpaRepository<RoleModuleVisibility, Long> {

    List<RoleModuleVisibility> findByRoleCode(String roleCode);

    /**
     * Every rule that applies to a person holding any of these roles.
     *
     * <p>Taken as a set because people hold more than one — the account
     * everybody calls HR holds IT_MGR, and PIX-E058 holds IT_EMP and IT_HR
     * together. What that means for a module is decided in the service, not
     * here.
     */
    List<RoleModuleVisibility> findByRoleCodeIn(Collection<String> roleCodes);

    List<RoleModuleVisibility> findAllByOrderByRoleCodeAscModuleCodeAsc();
}
