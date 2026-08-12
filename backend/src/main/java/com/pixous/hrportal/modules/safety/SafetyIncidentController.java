package com.pixous.hrportal.modules.safety;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.safety.dto.SafetyIncidentRequest;
import com.pixous.hrportal.modules.safety.dto.SafetyIncidentResponse;
import com.pixous.hrportal.modules.safety.dto.SafetyResolutionRequest;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Safety Incidents API.
 *  - Any authenticated user can report an incident and view their own.
 *  - Staff with REPORT_VIEW can view all incidents and resolve them.
 */
@RestController
@RequestMapping("/api/safety-incidents")
@RequiredArgsConstructor
@Tag(name = "Safety Incidents", description = "Employee safety incident reporting; staff investigation and resolution")
public class SafetyIncidentController {

    private final SafetyIncidentService service;

    @PostMapping
    @Operation(summary = "Report a safety incident")
    public ApiResponse<SafetyIncidentResponse> report(@Valid @RequestBody SafetyIncidentRequest req) {
        return ApiResponse.ok(service.report(SecurityUtils.currentUserId(), req),
                "Incident reported successfully");
    }

    @GetMapping("/mine")
    @Operation(summary = "List the current user's safety incident reports")
    public ApiResponse<PageResponse<SafetyIncidentResponse>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.myReports(SecurityUtils.currentUserId(), page, size));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Staff: list all safety incidents (filterable)")
    public ApiResponse<PageResponse<SafetyIncidentResponse>> all(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String incidentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.all(status, incidentType, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single safety incident")
    public ApiResponse<SafetyIncidentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Staff: resolve / update a safety incident's status")
    public ApiResponse<SafetyIncidentResponse> resolve(
            @PathVariable Long id, @Valid @RequestBody SafetyResolutionRequest req) {
        return ApiResponse.ok(service.resolve(SecurityUtils.currentUserId(), id, req),
                "Incident updated");
    }
}
