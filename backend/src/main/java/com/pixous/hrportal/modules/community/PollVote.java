package com.pixous.hrportal.modules.community;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One person's single vote on a poll. Voting again moves the vote. */
@Getter
@Setter
@Entity
@Table(name = "community_poll_votes")
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "option_index", nullable = false)
    private Integer optionIndex;

    @Column(name = "voted_at", insertable = false, updatable = false)
    private LocalDateTime votedAt;
}
