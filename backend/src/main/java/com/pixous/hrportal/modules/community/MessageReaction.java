package com.pixous.hrportal.modules.community;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One person's one emoji on one message. */
@Getter
@Setter
@Entity
@Table(name = "community_message_reactions")
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
