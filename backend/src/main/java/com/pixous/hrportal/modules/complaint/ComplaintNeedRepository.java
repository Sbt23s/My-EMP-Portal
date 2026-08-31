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

    /**
     * The same list, narrowed to one reviewer.
     *
     * <p>A complaint is addressed to a particular person -- HR or the CTO --
     * and naming them is the whole point of the field. filterAll returns every
     * complaint in the company to anyone holding COMPLAINT_MANAGE, so HR could
     * read complaints somebody had deliberately sent past them to the CTO. The
     * page filtered them out of the tab, but the rows had already crossed the
     * wire.
     *
     * <p>Addressed to them, or raised by them. Their own submissions stay
     * visible because they are already theirs to see.
     */
    @Query("""
            SELECT c FROM ComplaintNeed c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:kind   IS NULL OR c.kind = :kind)
              AND (c.requestedTo = :viewerId OR c.raisedBy = :viewerId)
            ORDER BY c.createdAt DESC
            """)
    Page<ComplaintNeed> filterForViewer(@Param("status") String status,
                                        @Param("kind") String kind,
                                        @Param("viewerId") Long viewerId,
                                        Pageable pageable);
}
