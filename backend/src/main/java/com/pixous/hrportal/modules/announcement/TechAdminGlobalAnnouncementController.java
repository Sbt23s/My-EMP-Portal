package com.pixous.hrportal.modules.announcement;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tech-admin/global-announcements")
@RequiredArgsConstructor
public class TechAdminGlobalAnnouncementController {

    private final GlobalLoginAnnouncementService announcementService;

    @GetMapping
    public ApiResponse<List<GlobalLoginAnnouncement>> listAll() {
        return ApiResponse.ok(announcementService.listAll());
    }

    @PostMapping
    public ApiResponse<GlobalLoginAnnouncement> createAndPublish(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("mediaType") String mediaType,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "targetRoles", required = false) String targetRoles,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestParam(value = "publishImmediately", required = false) Boolean publishImmediately,
            @RequestParam(value = "effectFile", required = false) MultipartFile effectFile,
            @RequestParam(value = "effectEnabled", required = false) Boolean effectEnabled,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long createdBy = principal != null ? principal.getId() : null;
        String createdByName = principal != null ? principal.getUsername() : "Tech Admin";

        GlobalLoginAnnouncement ann = announcementService.createAndPublish(
                file, mediaType, title, description, targetRoles, durationSeconds,
                createdBy, createdByName, publishImmediately, effectFile, effectEnabled
        );
        return ApiResponse.ok(ann);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<GlobalLoginAnnouncement> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.getOrDefault("status", "INACTIVE");
        return ApiResponse.ok(announcementService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        announcementService.deleteAnnouncement(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
