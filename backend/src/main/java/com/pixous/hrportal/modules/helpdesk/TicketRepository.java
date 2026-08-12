package com.pixous.hrportal.modules.helpdesk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findByRaisedByOrderByCreatedAtDesc(Long raisedBy, Pageable pageable);
    Page<Ticket> findByAssignedToOrderByCreatedAtDesc(Long assignedTo, Pageable pageable);
    Page<Ticket> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Ticket> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByAssignedToAndStatusNot(Long assignedTo, String status);
    long countByRaisedByAndStatusNot(Long raisedBy, String status);
    long countByStatusNot(String status);
}
