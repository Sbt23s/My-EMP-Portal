package com.pixous.hrportal.modules.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyModuleRepository extends JpaRepository<CompanyModule, Long> {
    List<CompanyModule> findByCompanyId(Long companyId);
    Optional<CompanyModule> findByCompanyIdAndModuleCode(Long companyId, String moduleCode);
}
