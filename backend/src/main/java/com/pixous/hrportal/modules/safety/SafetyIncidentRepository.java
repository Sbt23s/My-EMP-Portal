package com.pixous.hrportal.modules.safety;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SafetyIncidentRepository extends JpaRepository<SafetyIncident, Long> {

    Page<SafetyIncident> findByReportedByOrderByCreatedAtDesc(Long reportedBy, Pageable pageable);

    long countByStatus(String status);

    @Query("""
            SELECT s FROM SafetyIncident s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:incidentType IS NULL OR s.incidentType = :incidentType)
            ORDER BY s.createdAt DESC
            """)
    Page<SafetyIncident> filterAll(@Param("status") String status,
                                   @Param("incidentType") String incidentType,
                                   Pageable pageable);
}
