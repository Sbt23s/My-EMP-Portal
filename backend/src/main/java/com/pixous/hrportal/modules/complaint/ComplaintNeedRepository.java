package com.pixous.hrportal.modules.complaint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintNeedRepository extends JpaRepository<ComplaintNeed, Long> {

    Page<ComplaintNeed> findByRaisedByOrderByCreatedAtDesc(Long raisedBy, Pageable pageable);

    long countByStatus(String status);

    /** Highest reference code for a given year prefix (e.g. "CN-2026-"), or null if none. */
    @Query("SELECT MAX(c.referenceCode) FROM ComplaintNeed c WHERE c.referenceCode LIKE CONCAT(:prefix, '%')")
    String findMaxReferenceCode(@Param("prefix") String prefix);

    @Query("""
            SELECT c FROM ComplaintNeed c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:kind   IS NULL OR c.kind = :kind)
            ORDER BY c.createdAt DESC
            """)
    Page<ComplaintNeed> filterAll(@Param("status") String status,
                                  @Param("kind") String kind,
                                  Pageable pageable);
}
