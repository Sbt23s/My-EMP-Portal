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
     * Every record the CTO may see, whatever state it is in.
     *
     * <p>This used to return OPEN and UNDER_REVIEW only, on the reasoning that
     * a review queue is a list of decisions still owed. The effect was that a
     * record vanished from the CTO's screen the moment they reviewed it — they
     * pressed Save, the row disappeared, and there was no way back to what they
     * had just written. Filtering the page by Resolved showed "No discipline
     * records yet" on a company that had two.
     *
     * <p>A record is not finished when it is decided; it is the decision. The
     * page has a status filter and a Resolved tile precisely so somebody can
     * look back at one, and both were reading a list that could never contain
     * them.
     *
     * <p>CANCELLED is still excluded. Those were withdrawn before anybody acted,
     * and they are visible to whoever raised them under their own tab — the
     * CTO was never asked about them.
     */
    @Query("""
            SELECT d FROM DisciplineRecord d
            WHERE d.status <> 'CANCELLED'
            ORDER BY d.incidentDate DESC, d.id DESC
            """)
    List<DisciplineRecord> findPendingReview();
}
