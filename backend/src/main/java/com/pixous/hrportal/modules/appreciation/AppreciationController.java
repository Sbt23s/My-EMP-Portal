package com.pixous.hrportal.modules.appreciation;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.appreciation.dto.AppreciationDtos;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Appreciation letters.
 *
 * <p>HR, the administrators and the CTO write them; the named employee reads
 * their own. The role checks here decide who may call at all; the service
 * decides which rows come back, because "letters about me" is a question about
 * the rows rather than about the endpoint.
 */
@RestController
@RequestMapping("/api/appreciation")
@RequiredArgsConstructor
@Tag(name = "Appreciation", description = "Appreciation letters issued to employees")
public class AppreciationController {

    private final AppreciationService service;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "Write an appreciation letter, as a draft or sent")
    public ApiResponse<AppreciationDtos.View> create(
            @Valid @RequestBody AppreciationDtos.CreateRequest req) {
        return ApiResponse.ok(service.create(SecurityUtils.currentUserId(), req),
                Boolean.TRUE.equals(req.send()) ? "Appreciation letter sent" : "Draft saved");
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "Every issued letter")
    public ApiResponse<List<AppreciationDtos.View>> all() {
        return ApiResponse.ok(service.all());
    }

    /** The letters written about the person asking. Any authenticated user. */
    @GetMapping("/mine")
    @Operation(summary = "The current user's own appreciation letters")
    public ApiResponse<List<AppreciationDtos.View>> mine() {
        return ApiResponse.ok(service.mine(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One letter, if it is yours to read")
    public ApiResponse<AppreciationDtos.View> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "Send a letter that was saved as a draft")
    public ApiResponse<AppreciationDtos.View> send(@PathVariable Long id) {
        return ApiResponse.ok(service.send(SecurityUtils.currentUserId(), id), "Sent");
    }

    /** Recorded when the employee downloads their own letter. */
    @PostMapping("/{id}/downloaded")
    @Operation(summary = "Record that the employee downloaded their letter")
    public ApiResponse<Void> markDownloaded(@PathVariable Long id) {
        service.markDownloaded(SecurityUtils.currentUserId(), id);
        return ApiResponse.message("Recorded");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "Delete a draft")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.message("Draft deleted");
    }
}
