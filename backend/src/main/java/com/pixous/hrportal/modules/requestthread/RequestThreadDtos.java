package com.pixous.hrportal.modules.requestthread;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Payloads and views for the attachment and comment thread. */
public final class RequestThreadDtos {

    private RequestThreadDtos() {}

    /** A file, as the client sees it. */
    public record AttachmentView(
            Long id,
            String fileName,
            String contentType,
            Long fileSize,
            boolean image,
            String url,
            String uploadedByName,
            LocalDateTime uploadedAt
    ) {}

    /** One message in the thread. */
    public record CommentView(
            Long id,
            Long authorId,
            String authorName,
            String authorCode,
            String message,
            String attachmentUrl,
            LocalDateTime createdAt
    ) {}

    /** Posting a message. */
    public record CommentRequest(
            @NotBlank(message = "Write something before sending")
            @Size(max = 4000, message = "That message is too long")
            String message,
            /** An already-uploaded path, from the upload endpoint. Optional. */
            String attachmentPath
    ) {}
}
