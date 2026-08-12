package com.pixous.hrportal.modules.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    List<PollVote> findByMessageIdIn(List<Long> messageIds);
    Optional<PollVote> findByMessageIdAndUserId(Long messageId, Long userId);
}
