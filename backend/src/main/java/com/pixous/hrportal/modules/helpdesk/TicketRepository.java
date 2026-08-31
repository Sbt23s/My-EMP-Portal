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
    long countByAssignedToAndStatusNot(Long assignedTo, String status);
    long countByRaisedByAndStatusNot(Long raisedBy, String status);
    long countByStatusNot(String status);
}
