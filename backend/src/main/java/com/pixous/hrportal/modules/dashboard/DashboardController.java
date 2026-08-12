package com.pixous.hrportal.modules.dashboard;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.dashboard.dto.EmployeeDashboard;
import com.pixous.hrportal.modules.dashboard.dto.ExecutiveDashboard;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    /** Personal widgets for the logged-in employee. */
    @GetMapping("/me")
    public ApiResponse<EmployeeDashboard> me() {
        return ApiResponse.ok(service.employee(SecurityUtils.currentUserId()));
    }

    /**
     * Upcoming birthdays + work anniversaries (visible to every employee).
     * An {@code industry} narrows it to that side of the company; leaving it off
     * covers everybody, which is what every existing caller does.
     */
    @GetMapping("/celebrations")
    public ApiResponse<java.util.List<com.pixous.hrportal.modules.dashboard.dto.Celebration>> celebrations(
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(service.celebrations(industry));
    }

    /**
     * The organisation at a glance — joiners, probation, exits, today's
     * attendance broken down, and how the company is distributed and growing.
     * Read by whoever runs the organisation dashboard: an admin, HR, or the
     * company head.
     */
    @GetMapping("/org-insights")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','DASHBOARD_EXEC','ATTENDANCE_TEAM')")
    public ApiResponse<com.pixous.hrportal.modules.dashboard.dto.OrgInsights> orgInsights(
            @RequestParam(required = false) String industry) {
        return ApiResponse.ok(service.orgInsights(industry));
    }

    /** Org-wide KPIs — restricted to executive / leadership roles. */
    @GetMapping("/executive")
    @PreAuthorize("hasAuthority('DASHBOARD_EXEC')")
    public ApiResponse<ExecutiveDashboard> executive(@RequestParam(required = false) String industry) {
        return ApiResponse.ok(service.executive(industry));
    }
}
