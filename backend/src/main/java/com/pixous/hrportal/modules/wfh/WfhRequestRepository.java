package com.pixous.hrportal.modules.wfh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WfhRequestRepository extends JpaRepository<WfhRequest, Long> {

    /** What this person asked for, newest first. */
    List<WfhRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Everything addressed to one approver, whatever its state. */
    List<WfhRequest> findByRequestedToOrderByCreatedAtDesc(Long requestedTo);

    /** Everything, for HR and the CTO. */
    List<WfhRequest> findAllByOrderByCreatedAtDesc();

    /**
     * Whether this person already has a request covering any day in a range.
     *
     * <p>Two records overlap when each starts on or before the other ends —
     * comparing start dates alone misses a three-day request that swallows the
     * new one whole. APPROVED and PENDING count; a rejected or cancelled
     * request never claimed the days, so it must not block a fresh one.
     */
    @Query("""
            SELECT r FROM WfhRequest r
            WHERE r.userId = :userId
              AND r.status IN ('APPROVED','PENDING')
              AND r.fromDate <= :to AND r.toDate >= :from
            ORDER BY r.fromDate ASC
            """)
    List<WfhRequest> findOverlapping(@Param("userId") Long userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * Who is working from home on a given day.
     *
     * <p>The status board's only query, and the reason for the
     * (status, from_date, to_date) index.
     */
    @Query("""
            SELECT r FROM WfhRequest r
            WHERE r.status = 'APPROVED'
              AND r.fromDate <= :day AND r.toDate >= :day
            ORDER BY r.fromDate ASC
            """)
    List<WfhRequest> findActiveOn(@Param("day") LocalDate day);

    /**
     * Everyone working from home at any point in a range.
     *
     * <p>A request counts when it overlaps the window at all -- each starting
     * on or before the other ends. Asking day by day would miss a three-day
     * request that begins before the window and runs into it.
     */
    @Query("""
            SELECT r FROM WfhRequest r
            WHERE r.status = 'APPROVED'
              AND r.fromDate <= :to AND r.toDate >= :from
            ORDER BY r.fromDate ASC
            """)
    List<WfhRequest> findActiveBetween(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
