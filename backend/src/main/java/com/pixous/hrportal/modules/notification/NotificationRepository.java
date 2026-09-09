package com.pixous.hrportal.modules.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    void markAllRead(@Param("userId") Long userId);

    /**
     * Empty one person's notification list.
     *
     * <p>Scoped to the user id in the query itself rather than loading and
     * checking in Java: this is a delete, and a delete whose scope depends on
     * a filter applied afterwards is one bad refactor away from clearing
     * everybody's.
     *
     * <p>Read and unread alike. "Clear all" that leaves the unread ones behind
     * is not what the button says, and somebody pressing it after reading a
     * backlog wants the list gone, not tidied.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
