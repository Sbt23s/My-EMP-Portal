package com.pixous.hrportal.modules.discipline;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DisciplineRecordRepository extends JpaRepository<DisciplineRecord, Long> {

    /** An employee's own records, newest first. Nobody else's, ever. */
    List<DisciplineRecord> findByEmployeeIdOrderByIncidentDateDescIdDesc(Long employeeId);

    /**
     * Highest reference code for a year prefix, or null.
     *
     * <p>The next code counts up from this rather than from the row count:
     * count()+1 regenerates an already-used code the moment anything is
     * deleted, and the column is unique, so the insert would fail.
     */
    @Query("SELECT MAX(d.referenceCode) FROM DisciplineRecord d WHERE d.referenceCode LIKE CONCAT(:prefix, '%')")
    String findMaxReferenceCode(@Param("prefix") String prefix);

    /** Everything, for HR and the CTO, optionally narrowed by status. */
    @Query("""
            SELECT d FROM DisciplineRecord d
            WHERE (:status IS NULL OR d.status = :status)
            ORDER BY d.incidentDate DESC, d.id DESC
            """)
    Page<DisciplineRecord> filterAll(@Param("status") String status, Pageable pageable);

    /**
     * What the CTO has still to look at.
     *
     * <p>Cancelled records are left out: they were withdrawn before anybody
     * acted on them, and a review queue is a list of decisions still owed.
     */
    @Query("""
            SELECT d FROM DisciplineRecord d
            WHERE d.status IN ('OPEN', 'UNDER_REVIEW')
            ORDER BY d.incidentDate DESC, d.id DESC
            """)
    List<DisciplineRecord> findPendingReview();
}
