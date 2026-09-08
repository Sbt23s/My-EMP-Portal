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

    /**
     * The same list for somebody on the HR desk: anything addressed to any of
     * them, plus their own submissions.
     *
     * <p>HR is a desk, not a person. The recipient picker offers a single
     * "HR (code)" entry, so every complaint sent to HR carries one account's
     * id -- and with filterForViewer that meant only that one account could
     * read it. A colleague on the same desk, holding the same permission and
     * able to act on it perfectly well, saw an empty queue.
     *
     * <p>This is deliberately not filterAll. The distinction filterForViewer
     * exists to protect is between HR and the CTO: somebody who addresses a
     * complaint past HR to the CTO must not have it read by HR, and widening to
     * the desk keeps that intact because the CTO is not on the desk. What
     * widens is who counts as "HR", not what HR may see.
     *
     * @param deskIds every account on the HR desk, including the viewer
     */
    @Query("""
            SELECT c FROM ComplaintNeed c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:kind   IS NULL OR c.kind = :kind)
              AND (c.requestedTo IN :deskIds OR c.raisedBy = :viewerId)
            ORDER BY c.createdAt DESC
            """)
    Page<ComplaintNeed> filterForDesk(@Param("status") String status,
                                      @Param("kind") String kind,
                                      @Param("viewerId") Long viewerId,
                                      @Param("deskIds") java.util.Collection<Long> deskIds,
                                      Pageable pageable);
}
