package com.pixous.hrportal.modules.community;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommunityMessageRepository extends JpaRepository<CommunityMessage, Long> {
    List<CommunityMessage> findByCommunityIdOrderBySentAtAsc(Long communityId);
    void deleteBySender_Id(Long senderId);

    /** Pinned messages in a room, newest pin first. */
    List<CommunityMessage> findByCommunityIdAndPinnedAtIsNotNullOrderByPinnedAtDesc(Long communityId);

    /**
     * Messages in a room whose text matches, newest first. Searching is what
     * makes a long history worth keeping — without it, anything said last month
     * is effectively gone.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT m FROM CommunityMessage m
            WHERE m.community.id = :communityId
              AND LOWER(m.content) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY m.sentAt DESC
            """)
    List<CommunityMessage> search(@org.springframework.data.repository.query.Param("communityId") Long communityId,
                                  @org.springframework.data.repository.query.Param("q") String q);

    /** Scheduled messages whose time has come and which have not been announced. */
    List<CommunityMessage> findByScheduledAtIsNotNullAndScheduledAtLessThanEqual(java.time.LocalDateTime now);

    /** Everything in a room older than the retention cut-off. */
    List<CommunityMessage> findByCommunityIdAndSentAtBefore(Long communityId, java.time.LocalDateTime before);

    List<CommunityMessage> findBySentAtBefore(java.time.LocalDateTime before);
}
