package com.pixous.hrportal.modules.community;

import com.pixous.hrportal.modules.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "community_messages")
@Getter
@Setter
public class CommunityMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private CommunityGroup community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** For voice messages: served path of the recorded audio clip. Null for text. */
    @Column(name = "audio_path", length = 255)
    private String audioPath;

    /** Comma-separated upload paths — images, video, PDFs or documents. */
    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(name = "sent_at", insertable = false, updatable = false)
    private LocalDateTime sentAt;

    /** The message this one answers. Null for a top-level post. */
    @Column(name = "parent_id")
    private Long parentId;

    /** When it was pinned to the top of its room, and by whom. */
    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;
    @Column(name = "pinned_by")
    private Long pinnedBy;

    /**
     * Set in the future to hold the message back until then. It is stored
     * straight away but neither shown nor notified until the time comes.
     */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** An announcement people must confirm they have read. */
    @Column(name = "requires_ack", nullable = false)
    private boolean requiresAck = false;

    /** A poll's choices as a JSON array of labels; null for a normal message. */
    @Column(name = "poll_options", columnDefinition = "TEXT")
    private String pollOptions;
}
