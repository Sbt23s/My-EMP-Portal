package com.pixous.hrportal.modules.helpdesk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ticket_comments")
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "attachment_path", length = 255)
    private String attachmentPath;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
