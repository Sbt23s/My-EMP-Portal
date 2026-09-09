package com.pixous.hrportal.modules.notification;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public PageResponse<NotificationResponse> feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(SecurityUtils.currentUserId(), page, size);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        long count = service.unreadCount(SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("count", count));
    }

    @PostMapping("/mark-all-read")
    public ApiResponse<Void> markAllRead() {
        service.markAllRead(SecurityUtils.currentUserId());
        return ApiResponse.ok(null);
    }

    /**
     * Empty the signed-in person's notification list.
     *
     * <p>No path or body: the only list this can clear is the caller's own,
     * taken from the token. An id parameter here would be an invitation to
     * pass somebody else's.
     */
    @DeleteMapping
    public ApiResponse<Map<String, Integer>> clearAll() {
        int removed = service.clearAll(SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("cleared", removed));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        service.markRead(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(null);
    }
}
