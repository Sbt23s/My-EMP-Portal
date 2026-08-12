package com.pixous.hrportal.modules.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TechnicalAuditLogRepository extends JpaRepository<TechnicalAuditLog, Long> {
    List<TechnicalAuditLog> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<TechnicalAuditLog> findAllByOrderByCreatedAtDesc();
}
