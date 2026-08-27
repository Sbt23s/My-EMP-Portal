package com.pixous.hrportal.modules.requestthread;

import com.pixous.hrportal.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Files and conversation on a leave or permission request.
 *
 * <p>{@code type} is LEAVE or PERMISSION. One set of routes for both, because
 * the records differ only in what they hang off, and two sets would mean two
 * of every future change.
 *
 * <p>No {@code @PreAuthorize} here on purpose. These are not gated by a
 * permission but by a relationship — you may read a request you raised, one
 * addressed to you, or any if you oversee the process — and that is decided in
 * the service, once, so no route can forget it.
 */
@RestController
@RequestMapping("/api/requests/{type}/{id}")
@RequiredArgsConstructor
@Tag(name = "Request thread", description = "Attachments and comments on leave and permission requests")
public class RequestThreadController {

    private final RequestThreadService service;

    @GetMapping("/attachments")
    @Operation(summary = "Files attached to this request")
    public ApiResponse<List<RequestThreadDtos.AttachmentView>> attachments(
            @PathVariable String type, @PathVariable Long id) {
        return ApiResponse.ok(service.listAttachments(type, id));
    }

    @PostMapping(value = "/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Attach a photograph or document")
    public ApiResponse<RequestThreadDtos.AttachmentView> attach(
            @PathVariable String type, @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.attach(type, id, file), "Attached");
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @Operation(summary = "Remove a file you uploaded")
    public ApiResponse<Void> removeAttachment(
            @PathVariable String type, @PathVariable Long id,
            @PathVariable Long attachmentId) {
        service.deleteAttachment(attachmentId);
        return ApiResponse.message("Removed");
    }

    @GetMapping("/comments")
    @Operation(summary = "The conversation about this request")
    public ApiResponse<List<RequestThreadDtos.CommentView>> comments(
            @PathVariable String type, @PathVariable Long id) {
        return ApiResponse.ok(service.listComments(type, id));
    }

    @PostMapping("/comments")
    @Operation(summary = "Say something about this request")
    public ApiResponse<RequestThreadDtos.CommentView> comment(
            @PathVariable String type, @PathVariable Long id,
            @Valid @RequestBody RequestThreadDtos.CommentRequest req) {
        return ApiResponse.ok(service.comment(type, id, req), "Sent");
    }
}
