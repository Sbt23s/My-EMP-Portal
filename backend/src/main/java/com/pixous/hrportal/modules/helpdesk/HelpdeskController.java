package com.pixous.hrportal.modules.helpdesk;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.helpdesk.dto.*;
import com.pixous.hrportal.common.StorageService;
import com.pixous.hrportal.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class HelpdeskController {

    private final HelpdeskService service;
    private final StorageService storageService;

    @GetMapping
    public PageResponse<TicketResponse> myTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.myTickets(SecurityUtils.currentUserId(), page, size);
    }

    @PostMapping
    public ApiResponse<TicketResponse> create(@Valid @RequestBody TicketRequest req) {
        return ApiResponse.ok(service.raise(SecurityUtils.currentUserId(), req), "Ticket raised");
    }

    /** Screenshot or document for a ticket. Returns the stored path. */
    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("path", storageService.store(file, "ticket-attachments")),
                "Attachment uploaded");
    }

    /**
     * The people a support request can be addressed to. Open to any signed-in
     * user — an employee needs this to raise a ticket, and the full user
     * directory is not theirs to read.
     */
    @GetMapping("/agents")
    public ApiResponse<List<Map<String, Object>>> agents() {
        return ApiResponse.ok(service.agents(SecurityUtils.currentUserId()));
    }

    @GetMapping("/assigned-to-me")
    @PreAuthorize("hasAuthority('HELPDESK_AGENT')")
    public PageResponse<TicketResponse> agentQueue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.agentQueue(SecurityUtils.currentUserId(), status, page, size);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('HELPDESK_AGENT','USER_MANAGE','DASHBOARD_EXEC')")
    public PageResponse<TicketResponse> allTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.allTickets(SecurityUtils.currentUserId(), status, page, size);
    }

    /** The raiser edits their own ticket while it is still open. */
    @PutMapping("/{id}")
    public ApiResponse<TicketResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody TicketRequest req) {
        return ApiResponse.ok(service.updateOwn(SecurityUtils.currentUserId(), id, req),
                "Ticket updated");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancelOwn(SecurityUtils.currentUserId(), id);
        return ApiResponse.message("Ticket cancelled");
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<CommentResponse> comment(
            @PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return ApiResponse.ok(service.addComment(SecurityUtils.currentUserId(), id, req),
                "Comment added");
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('HELPDESK_AGENT')")
    public ApiResponse<TicketResponse> status(
            @PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return ApiResponse.ok(service.changeStatus(SecurityUtils.currentUserId(), id, req),
                "Status updated");
    }

    @PostMapping("/{id}/rating")
    public ApiResponse<TicketResponse> rate(
            @PathVariable Long id, @Valid @RequestBody RatingRequest req) {
        return ApiResponse.ok(service.rate(SecurityUtils.currentUserId(), id, req), "Thanks for the feedback");
    }
}
