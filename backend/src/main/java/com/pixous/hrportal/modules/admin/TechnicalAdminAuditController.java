package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/technical-admin/audit-logs")
@PreAuthorize("hasRole('TECHNICAL_ADMIN')")
public class TechnicalAdminAuditController {

    private final TechnicalAuditLogRepository auditLogRepository;

    public TechnicalAdminAuditController(TechnicalAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllAuditLogs() {
        return ResponseEntity.ok(ApiResponse.ok(auditLogRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<?>> getCompanyAuditLogs(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)));
    }
}
