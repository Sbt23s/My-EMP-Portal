package com.pixous.hrportal.modules.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {
    List<MessageRead> findByMessageIdIn(List<Long> messageIds);
    List<MessageRead> findByMessageId(Long messageId);
    Optional<MessageRead> findByMessageIdAndUserId(Long messageId, Long userId);
}
