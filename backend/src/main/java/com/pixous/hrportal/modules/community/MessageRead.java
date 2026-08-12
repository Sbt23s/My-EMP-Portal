package com.pixous.hrportal.modules.community;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * That somebody has seen a message, and — for an announcement that asks for it —
 * that they have confirmed reading it. Seeing and confirming are separate: the
 * first happens on its own, the second only when the button is pressed.
 */
@Getter
@Setter
@Entity
@Table(name = "community_message_reads")
public class MessageRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_at", insertable = false, updatable = false)
    private LocalDateTime readAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
}
