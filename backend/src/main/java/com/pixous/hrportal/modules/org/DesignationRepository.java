package com.pixous.hrportal.modules.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    List<Designation> findByActiveTrueOrderByNameAsc();

    /** Active designations for a given industry; pass {@code null} to get them all. */
    @Query("""
            SELECT d FROM Designation d
            WHERE d.active = true
              AND (:industry IS NULL OR d.industry = :industry)
            ORDER BY d.name ASC
            """)
    List<Designation> findActiveByIndustry(@Param("industry") String industry);
}
