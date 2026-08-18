package com.pixous.hrportal.modules.announcement;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/global-announcements")
@RequiredArgsConstructor
public class GlobalLoginAnnouncementController {

    private final GlobalLoginAnnouncementService announcementService;

    @GetMapping("/active")
    public ApiResponse<GlobalLoginAnnouncement> getActiveAnnouncement(@AuthenticationPrincipal UserPrincipal principal) {
        String role = "Employee";
        if (principal != null && principal.getAuthorities() != null && !principal.getAuthorities().isEmpty()) {
            role = principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }
        Optional<GlobalLoginAnnouncement> active = announcementService.getActiveForUserRole(role);
        return ApiResponse.ok(active.orElse(null));
    }
}
