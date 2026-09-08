package com.pixous.hrportal.modules.dashboard;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.dashboard.dto.EmployeeDashboard;
import com.pixous.hrportal.modules.dashboard.dto.ExecutiveDashboard;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * Every birthday and work anniversary in one calendar year.
     *
     * <p>A separate endpoint from the card above, because that one is a
     * "coming up soon" widget -- sixty days, twelve rows -- and this is a
     * register somebody reads a year at a time, dates already past included.
     * Visible to every employee, as the card is: whose birthday it is has
     * never been private here, and a team that cannot see the year cannot plan
     * around it.
     */
    @GetMapping("/celebrations/year/{year}")
    public ApiResponse<java.util.List<com.pixous.hrportal.modules.dashboard.dto.Celebration>> celebrationsInYear(
            @PathVariable int year,
            @RequestParam(required = false) String industry) {
        // A path variable is whatever the caller typed. Bounded so a typo asks
        // for a year rather than sending withYear a value it will throw on.
        if (year < 1970 || year > 2200) {
            throw com.pixous.hrportal.common.ApiException.business(
                    "Choose a year between 1970 and 2200.");
        }
        return ApiResponse.ok(service.celebrationsInYear(year, industry));
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
