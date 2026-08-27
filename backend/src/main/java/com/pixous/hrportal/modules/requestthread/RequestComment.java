package com.pixous.hrportal.modules.requestthread;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One message in the conversation about a leave or permission request.
 *
 * <p>Deliberately not the same thing as {@code decisionComment}, which is the
 * single line recorded with a decision and belongs to that decision. This is a
 * thread: an approver asks which client the day is for, the applicant answers,
 * and both can see both. Folding the two together would make a decision read
 * as the last message in a chat rather than as a decision.
 */
@Getter
@Setter
@Entity
@Table(name = "request_comments")
public class RequestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "attachment_path", length = 500)
    private String attachmentPath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
