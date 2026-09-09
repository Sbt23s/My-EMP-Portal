package com.pixous.hrportal.modules.notification;

import com.pixous.hrportal.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Persist a notification and push it to the user's private WebSocket topic.
     * Invoked asynchronously so callers (leave approval, asset allocation, etc.)
     * are never blocked by notification delivery.
     */
    @Async
    @Transactional
    public void createAndPush(Long userId, String title, String body, String type, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setBody(body);
        n.setType(type);
        n.setLink(link);
        Notification saved = repository.save(n);

        NotificationResponse payload = NotificationResponse.from(saved);
        // /user/{userId}/queue/notifications -> resolved per-session by Spring
        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId), "/queue/notifications", payload);
        // public per-user topic fallback for clients subscribing directly
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, payload);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        Page<NotificationResponse> result = repository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(NotificationResponse::from);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId);
    }

    /**
     * Empty this person's own notification list.
     *
     * <p>Their own, and only their own — the user id comes from the security
     * context at the controller, never from the request, so there is no shape
     * of call that clears somebody else's.
     *
     * <p>A real delete rather than a hidden flag. The list is a feed of things
     * that have already happened elsewhere: the leave request, the ticket and
     * the payslip all still exist in their own modules, and keeping a
     * tombstone of the announcement about them serves nobody.
     */
    @Transactional
    public int clearAll(Long userId) {
        return repository.deleteAllForUser(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        repository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> n.setRead(true));
    }
}
