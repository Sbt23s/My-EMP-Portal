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

    @Transactional
    public void markRead(Long userId, Long id) {
        repository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> n.setRead(true));
    }
}
