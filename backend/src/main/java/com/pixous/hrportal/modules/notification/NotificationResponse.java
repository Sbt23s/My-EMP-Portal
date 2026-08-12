package com.pixous.hrportal.modules.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String body,
        String type,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getBody(),
                n.getType(), n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
