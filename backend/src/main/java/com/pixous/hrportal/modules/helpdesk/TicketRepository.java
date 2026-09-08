package com.pixous.hrportal.modules.helpdesk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findByRaisedByOrderByCreatedAtDesc(Long raisedBy, Pageable pageable);
    Page<Ticket> findByAssignedToOrderByCreatedAtDesc(Long assignedTo, Pageable pageable);
    Page<Ticket> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Ticket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Tickets one agent is entitled to see: addressed to them, or raised by
     * them.
     *
     * <p>A ticket names who it is for -- the CTO, HR, or the system admin --
     * and the /all list ignored that, handing every ticket to anyone holding
     * HELPDESK_AGENT. Somebody raising a ticket about HR chooses the CTO for a
     * reason, and HR could read it anyway.
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (t.assignedTo = :viewerId OR t.raisedBy = :viewerId)
            ORDER BY t.createdAt DESC
            """)
    Page<Ticket> findForViewer(@Param("status") String status,
                               @Param("viewerId") Long viewerId,
                               Pageable pageable);

    /**
     * The same list for somebody on the HR desk: anything addressed to any of
     * them, plus what they raised themselves.
     *
     * <p>HR is a desk, not a person. The agent picker offers a single
     * "HR (code)" entry, so every ticket sent to HR carries one account's id,
     * and with findForViewer only that account could read it -- a colleague on
     * the same desk saw an empty queue and had no way to know a request was
     * waiting.
     *
     * <p>Deliberately not "every ticket". The distinction findForViewer exists
     * to protect is between HR and the CTO: a ticket somebody addressed past HR
     * to the CTO stays invisible to HR, because the CTO is not on the desk.
     * What widens is who counts as HR.
     *
     * @param deskIds every account on the HR desk, including the viewer
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (t.assignedTo IN :deskIds OR t.raisedBy = :viewerId)
            ORDER BY t.createdAt DESC
            """)
    Page<Ticket> findForDesk(@Param("status") String status,
                             @Param("viewerId") Long viewerId,
                             @Param("deskIds") java.util.Collection<Long> deskIds,
                             Pageable pageable);

    /**
     * The desk's own queue: tickets addressed to anybody on it, and not the
     * ones its members raised themselves.
     *
     * <p>This is what "Assigned to me" shows an HR user. Their own submissions
     * are excluded because those belong in "My tickets" -- a queue that lists
     * your own request back at you as work waiting for you is one people stop
     * trusting.
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND t.assignedTo IN :deskIds
            ORDER BY t.createdAt DESC
            """)
    Page<Ticket> findAssignedToDesk(@Param("status") String status,
                                    @Param("deskIds") java.util.Collection<Long> deskIds,
                                    Pageable pageable);
    long countByAssignedToAndStatusNot(Long assignedTo, String status);
    long countByRaisedByAndStatusNot(Long raisedBy, String status);
    long countByStatusNot(String status);
}
