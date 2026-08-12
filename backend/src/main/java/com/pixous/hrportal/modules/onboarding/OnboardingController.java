package com.pixous.hrportal.modules.onboarding;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.onboarding.dto.OnboardingChecklistResponse;
import com.pixous.hrportal.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/start")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<OnboardingChecklistResponse> startOnboarding(@PathVariable Long userId) {
        return ApiResponse.ok(service.startOnboarding(userId), "Onboarding started");
    }

    /**
     * IDs of employees currently in onboarding — used to scope announcement
     * channels. HR builds those channels too, so EMPLOYEE_MANAGE reads it as well.
     */
    @GetMapping("/employees")
    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE', 'COMMUNITY_MANAGE')")
    public ApiResponse<java.util.List<Long>> onboardingEmployees() {
        return ApiResponse.ok(service.onboardingUserIds());
    }

    @GetMapping("/{userId}")
    public ApiResponse<OnboardingChecklistResponse> getOnboarding(@PathVariable Long userId) {
        boolean isSelf = SecurityUtils.currentUserId().equals(userId);
        boolean isPrivileged = SecurityUtils.hasAuthority("USER_MANAGE");
        if (!isSelf && !isPrivileged) {
            throw com.pixous.hrportal.common.ApiException.business("You can only view your own onboarding checklist");
        }
        return ApiResponse.ok(service.getOnboarding(userId));
    }

    @PostMapping("/{userId}/tasks/{taskId}/complete")
    public ApiResponse<OnboardingChecklistResponse> completeTask(@PathVariable Long userId, @PathVariable Long taskId) {
        boolean isSelf = SecurityUtils.currentUserId().equals(userId);
        boolean isPrivileged = SecurityUtils.hasAuthority("USER_MANAGE");
        if (!isSelf && !isPrivileged) {
            throw com.pixous.hrportal.common.ApiException.business("You can only update your own onboarding checklist");
        }
        return ApiResponse.ok(service.completeTask(userId, taskId), "Task marked as completed");
    }
}
