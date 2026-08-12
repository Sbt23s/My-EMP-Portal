package com.pixous.hrportal.modules.complaint;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.complaint.dto.ComplaintDecisionRequest;
import com.pixous.hrportal.modules.complaint.dto.ComplaintRequest;
import com.pixous.hrportal.modules.complaint.dto.ComplaintResponse;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Complaints / Needs API.
 *  - Any authenticated user (employee or manager) can submit and view their own.
 *  - HR / Admin (USER_MANAGE) can view all submissions and respond.
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaints & Needs", description = "Employee/manager complaints and requirements; HR/Admin review")
public class ComplaintController {

    private final ComplaintService service;

    @PostMapping
    @Operation(summary = "Submit a complaint or need")
    public ApiResponse<ComplaintResponse> submit(@Valid @RequestBody ComplaintRequest req) {
        return ApiResponse.ok(service.submit(SecurityUtils.currentUserId(), req),
                "Submitted successfully");
    }

    @GetMapping("/recipients")
    @Operation(summary = "HR staff a complaint or need can be addressed to")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> recipients() {
        return ApiResponse.ok(service.recipients(SecurityUtils.currentUserId()));
    }

    @GetMapping("/mine")
    @Operation(summary = "List the current user's complaints/needs")
    public ApiResponse<PageResponse<ComplaintResponse>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.mySubmissions(SecurityUtils.currentUserId(), page, size));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE')")
    @Operation(summary = "HR/Admin: list all complaints/needs (filterable)")
    public ApiResponse<PageResponse<ComplaintResponse>> all(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.all(status, kind, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single complaint/need")
    public ApiResponse<ComplaintResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE')")
    @Operation(summary = "HR/Admin: respond to / update a complaint's status")
    public ApiResponse<ComplaintResponse> respond(
            @PathVariable Long id, @Valid @RequestBody ComplaintDecisionRequest req) {
        return ApiResponse.ok(service.respond(SecurityUtils.currentUserId(), id, req),
                "Updated");
    }
}
