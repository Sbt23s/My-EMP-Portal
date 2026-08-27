package com.pixous.hrportal.modules.wfh;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.wfh.dto.WfhDtos;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Work From Home requests.
 *
 * <p>Applying and reading your own is open to any signed-in person — everybody
 * can ask to work from home. Deciding is not gated by a permission but by the
 * request itself: only the person it was addressed to may act on it, which the
 * service checks. The two organisation-wide views are gated, because seeing
 * everybody's requests is a different thing from having one.
 */
@RestController
@RequestMapping("/api/wfh")
@RequiredArgsConstructor
@Tag(name = "Work From Home", description = "Apply for, approve and track work from home")
public class WfhController {

    private final WfhService service;

    @PostMapping
    @Operation(summary = "Apply to work from home")
    public ApiResponse<WfhDtos.WfhView> apply(@Valid @RequestBody WfhDtos.ApplyRequest req) {
        return ApiResponse.ok(service.apply(SecurityUtils.currentUserId(), req),
                "Work from home request submitted");
    }

    @GetMapping("/me")
    @Operation(summary = "My requests and their status")
    public ApiResponse<List<WfhDtos.WfhView>> mine() {
        return ApiResponse.ok(service.mine(SecurityUtils.currentUserId()));
    }

    @GetMapping("/for-me")
    @Operation(summary = "Requests addressed to me")
    public ApiResponse<List<WfhDtos.WfhView>> forMe() {
        return ApiResponse.ok(service.forMe(SecurityUtils.currentUserId()));
    }

    @GetMapping("/approvers")
    @Operation(summary = "Who a request from me would go to")
    public ApiResponse<List<Map<String, Object>>> approvers() {
        return ApiResponse.ok(service.approvers(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Approve or reject a request addressed to me")
    public ApiResponse<WfhDtos.WfhView> decide(@PathVariable Long id,
                                               @Valid @RequestBody WfhDtos.DecisionRequest req) {
        return ApiResponse.ok(service.decide(SecurityUtils.currentUserId(), id, req),
                Boolean.TRUE.equals(req.approve()) ? "Approved" : "Rejected");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Withdraw a request I raised")
    public ApiResponse<WfhDtos.WfhView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(SecurityUtils.currentUserId(), id), "Withdrawn");
    }

    /**
     * Everything, for HR, the CTO and the administrators.
     *
     * <p>DASHBOARD_EXEC is included because that is what the CTO account holds
     * for the executive views; USER_MANAGE covers HR and the administrators.
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "Every request, for HR and the CTO")
    public ApiResponse<List<WfhDtos.WfhView>> all() {
        return ApiResponse.ok(service.all(SecurityUtils.currentUserId()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','DASHBOARD_EXEC','ATTENDANCE_TEAM')")
    @Operation(summary = "Who is working from home on a given day")
    public ApiResponse<List<WfhDtos.WfhView>> active(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.activeOn(date, SecurityUtils.currentUserId()));
    }
}
