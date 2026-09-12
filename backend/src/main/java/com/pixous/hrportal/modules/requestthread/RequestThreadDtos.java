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

    /**
     * What the request is, for a page opened straight from a notification.
     *
     * <p>Enough to know what you are reading before you read the thread --
     * which kind of request it is, whose it is, when it is for and where it
     * has got to. A notification that lands somebody on a bare conversation
     * with no idea which request it belongs to is only half a link.
     */
    public record RequestSummary(
            String type,
            Long id,
            String reference,
            String employeeName,
            String employeeCode,
            String requestedToName,
            String status,
            String detail,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate,
            String reason,
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
