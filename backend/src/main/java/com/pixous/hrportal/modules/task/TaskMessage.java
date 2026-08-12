package com.pixous.hrportal.modules.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One line of the conversation kept against a task. Instructions, updates,
 * feedback and the files that go with them stay with the work they are about
 * rather than in somebody's inbox.
 */
@Getter
@Setter
@Entity
@Table(name = "task_messages")
public class TaskMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** Comma-separated upload paths; null when nothing is attached. */
    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();
}
