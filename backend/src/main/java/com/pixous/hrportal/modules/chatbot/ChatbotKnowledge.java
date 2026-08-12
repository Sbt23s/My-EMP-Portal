package com.pixous.hrportal.modules.chatbot;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A knowledge-base document the assistant grounds its answers on.
 * Rows come from three sources: {@code seed} (baked-in EMS guide),
 * {@code firecrawl} (crawled website pages) and {@code manual} (admin pasted).
 */
@Getter
@Setter
@Entity
@Table(name = "chatbot_knowledge")
public class ChatbotKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
