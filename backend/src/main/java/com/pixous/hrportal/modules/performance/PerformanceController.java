package com.pixous.hrportal.modules.performance;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.performance.dto.PerformanceGoalResponse;
import com.pixous.hrportal.modules.performance.dto.PerformanceReviewResponse;
import com.pixous.hrportal.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceService service;

    public PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @PostMapping("/goals")
    public ApiResponse<PerformanceGoalResponse> createGoal(@RequestParam String title, @RequestParam(required = false) String description) {
        return ApiResponse.ok(service.createGoal(SecurityUtils.currentUserId(), title, description), "Goal created");
    }

    @GetMapping("/goals/me")
    public ApiResponse<List<PerformanceGoalResponse>> myGoals() {
        return ApiResponse.ok(service.getMyGoals(SecurityUtils.currentUserId()));
    }

    @PostMapping("/reviews")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<PerformanceReviewResponse> createReview(@RequestParam Long userId, @RequestParam String period) {
        return ApiResponse.ok(service.createReview(userId, SecurityUtils.currentUserId(), period), "Review cycle created");
    }

    @GetMapping("/reviews/me")
    public ApiResponse<List<PerformanceReviewResponse>> myReviews() {
        return ApiResponse.ok(service.getMyReviews(SecurityUtils.currentUserId()));
    }

    @GetMapping("/reviews/team")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<List<PerformanceReviewResponse>> teamReviews() {
        return ApiResponse.ok(service.getTeamReviews(SecurityUtils.currentUserId()));
    }
}
