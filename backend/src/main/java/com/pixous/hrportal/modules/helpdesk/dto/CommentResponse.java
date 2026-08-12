package com.pixous.hrportal.modules.helpdesk.dto;

import com.pixous.hrportal.modules.helpdesk.TicketComment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id, Long authorId, String authorName, String comment,
        String attachmentPath, LocalDateTime createdAt
) {
    public static CommentResponse from(TicketComment c, String authorName) {
        return new CommentResponse(c.getId(), c.getAuthorId(), authorName,
                c.getComment(), c.getAttachmentPath(), c.getCreatedAt());
    }
}
