package com.pixous.hrportal.modules.leave;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.leave.dto.PermissionApplyRequest;
import com.pixous.hrportal.modules.leave.dto.PermissionResponse;
import com.pixous.hrportal.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService service;

    @PostMapping
    public ApiResponse<PermissionResponse> apply(@Valid @RequestBody PermissionApplyRequest req) {
        return ApiResponse.ok(service.apply(SecurityUtils.currentUserId(), req), "Permission requested");
    }

    @GetMapping("/me")
    public ApiResponse<List<PermissionResponse>> mine() {
        return ApiResponse.ok(service.mine(SecurityUtils.currentUserId()));
    }

    /** Approvers an employee can send a permission request to (managers/TLs/HR). */
    @GetMapping("/approvers")
    public ApiResponse<List<Map<String, Object>>> approvers() {
        return ApiResponse.ok(service.approvers(SecurityUtils.currentUserId()));
    }

    /**
     * Whether a date is free for a permission request, and why not if it is
     * not.
     *
     * <p>Read-only, and it decides nothing: apply() runs the same checks and
     * remains the authority. This exists so the form can say what is wrong
     * while the date is still on screen, rather than after a submit that was
     * never going to be accepted.
     */
    @GetMapping("/availability")
    public ApiResponse<Map<String, Object>> availability(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        return ApiResponse.ok(service.availability(SecurityUtils.currentUserId(), date));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(SecurityUtils.currentUserId(), id);
        return ApiResponse.message("Permission request cancelled");
    }

    /** Admin/HR: read-only view of every employee's permission requests, even
     *  ones addressed to a Team Leader (not just requests sent to me). */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasRole('IT_MGR') or hasRole('IT_HR')")
    public ApiResponse<List<PermissionResponse>> all() {
        return ApiResponse.ok(service.all());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public ApiResponse<List<PermissionResponse>> pending() {
        // Only the approver the request was addressed to sees it.
        return ApiResponse.ok(service.pendingFor(SecurityUtils.currentUserId()));
    }

    /** All requests addressed to the approver (any status) — full history/details. */
    @GetMapping("/for-me")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public ApiResponse<List<PermissionResponse>> forMe() {
        return ApiResponse.ok(service.forApprover(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public ApiResponse<PermissionResponse> decide(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean approve = "APPROVED".equalsIgnoreCase(String.valueOf(body.get("status")))
                || Boolean.TRUE.equals(body.get("approve"));
        String comment = body.get("comment") != null ? String.valueOf(body.get("comment")) : null;
        return ApiResponse.ok(service.decide(SecurityUtils.currentUserId(), id, approve, comment),
                approve ? "Permission approved" : "Permission rejected");
    }
}
