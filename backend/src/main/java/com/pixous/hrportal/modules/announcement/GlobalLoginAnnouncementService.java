package com.pixous.hrportal.modules.announcement;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalLoginAnnouncementService {

    private final GlobalLoginAnnouncementRepository repository;
    private final StorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public Optional<GlobalLoginAnnouncement> getActiveForUserRole(String role) {
        Optional<GlobalLoginAnnouncement> opt = repository.findFirstByStatusOrderByPublishedAtDesc("ACTIVE");
        if (opt.isEmpty()) return Optional.empty();

        GlobalLoginAnnouncement ann = opt.get();
        if (ann.getTargetRoles() != null && !ann.getTargetRoles().isBlank()) {
            String normalizedRole = role == null ? "Employee" : role.trim().toUpperCase();
            String target = ann.getTargetRoles().toUpperCase();

            boolean match = target.contains("ALL")
                    || target.contains(normalizedRole)
                    || (normalizedRole.contains("EMP") && target.contains("EMPLOYEE"))
                    || (normalizedRole.contains("TL") && target.contains("TL"))
                    || ((normalizedRole.contains("HR") || normalizedRole.contains("MGR")) && target.contains("HR"))
                    || ((normalizedRole.contains("ADMIN") || normalizedRole.contains("SUPER")) && target.contains("ADMIN"));

            if (!match) return Optional.empty();
        }
        return Optional.of(ann);
    }

    @Transactional(readOnly = true)
    public List<GlobalLoginAnnouncement> listAll() {
        return repository.findByStatusNotOrderByCreatedAtDesc("DELETED");
    }

    @Transactional
    public GlobalLoginAnnouncement createAndPublish(
            MultipartFile file,
            String mediaType,
            String title,
            String description,
            String targetRoles,
            Integer durationSeconds,
            Long createdBy,
            String createdByName,
            Boolean publishImmediately,
            MultipartFile effectFile,
            Boolean effectEnabled
    ) {
        String mediaUrl;
        String mediaName = null;
        Long mediaSize = null;

        if (file != null && !file.isEmpty()) {
            String originalFilename = file.getOriginalFilename();
            log.info("Uploading announcement media: {}, type: {}", originalFilename, file.getContentType());

            String storedPath = storageService.store(file, "announcements");
            if (storedPath.startsWith("/api/files/")) {
                mediaUrl = storedPath;
            } else if (storedPath.startsWith("api/files/")) {
                mediaUrl = "/" + storedPath;
            } else {
                mediaUrl = "/api/files/" + storedPath.replaceAll("^/+", "");
            }

            mediaName = originalFilename;
            mediaSize = file.getSize();
        } else {
            throw ApiException.business("Media file is required for announcement");
        }

        // The effect, if one was given. Stored beside the media and normalised
        // the same way, so both are served by the same /api/files route.
        String effectUrl = null;
        String effectName = null;
        Long effectSize = null;

        if (effectFile != null && !effectFile.isEmpty()) {
            String stored = storageService.store(effectFile, "announcements");
            effectUrl = stored.startsWith("/api/files/")
                    ? stored
                    : "/api/files/" + stored.replaceAll("^/+", "");
            effectName = effectFile.getOriginalFilename();
            effectSize = effectFile.getSize();
        }

        // An effect cannot be on without a file to play. Saying otherwise would
        // leave the popup waiting for an animation that does not exist.
        boolean playEffect = Boolean.TRUE.equals(effectEnabled) && effectUrl != null;

        boolean active = publishImmediately == null || publishImmediately;

        if (active) {
            List<GlobalLoginAnnouncement> activeList = repository.findByStatus("ACTIVE");
            for (GlobalLoginAnnouncement item : activeList) {
                item.setStatus("INACTIVE");
                repository.save(item);
            }
        }

        GlobalLoginAnnouncement ann = GlobalLoginAnnouncement.builder()
                .title(title)
                .description(description)
                .mediaType(mediaType != null ? mediaType.toUpperCase() : "IMAGE")
                .mediaUrl(mediaUrl)
                .mediaName(mediaName)
                .mediaSize(mediaSize)
                .effectUrl(effectUrl)
                .effectName(effectName)
                .effectSize(effectSize)
                .effectEnabled(playEffect)
                .status(active ? "ACTIVE" : "INACTIVE")
                .targetRoles(targetRoles != null && !targetRoles.isBlank() ? targetRoles : "Employee,TL,HR,Admin")
                .durationSeconds(durationSeconds != null && durationSeconds > 0 ? durationSeconds : 15)
                .createdBy(createdBy)
                .createdByName(createdByName)
                .publishedAt(active ? LocalDateTime.now() : null)
                .build();

        ann = repository.save(ann);

        if (active) {
            broadcastRealtimeEvent("PUBLISHED", ann);
        }

        return ann;
    }

    @Transactional
    public GlobalLoginAnnouncement updateStatus(Long id, String status) {
        GlobalLoginAnnouncement ann = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Announcement not found"));

        String newStatus = status.toUpperCase();
        if ("ACTIVE".equals(newStatus)) {
            List<GlobalLoginAnnouncement> activeList = repository.findByStatus("ACTIVE");
            for (GlobalLoginAnnouncement item : activeList) {
                if (!item.getId().equals(id)) {
                    item.setStatus("INACTIVE");
                    repository.save(item);
                }
            }
            ann.setStatus("ACTIVE");
            ann.setPublishedAt(LocalDateTime.now());
        } else {
            ann.setStatus("INACTIVE");
        }

        ann = repository.save(ann);
        broadcastRealtimeEvent("ACTIVE".equals(newStatus) ? "PUBLISHED" : "INACTIVATED", ann);
        return ann;
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        GlobalLoginAnnouncement ann = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Announcement not found"));

        ann.setStatus("DELETED");
        ann.setDeletedAt(LocalDateTime.now());
        repository.save(ann);

        broadcastRealtimeEvent("DELETED", ann);
    }

    private void broadcastRealtimeEvent(String action, GlobalLoginAnnouncement announcement) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", action);
            payload.put("id", announcement.getId());
            payload.put("title", announcement.getTitle());
            payload.put("description", announcement.getDescription());
            payload.put("mediaType", announcement.getMediaType());
            payload.put("mediaUrl", announcement.getMediaUrl());
            payload.put("status", announcement.getStatus());
            payload.put("targetRoles", announcement.getTargetRoles());
            payload.put("durationSeconds", announcement.getDurationSeconds());
            payload.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/global-announcement", payload);
            log.info("Broadcasted STOMP real-time announcement event: {} for id: {}", action, announcement.getId());
        } catch (Exception e) {
            log.error("Failed to broadcast STOMP real-time announcement event", e);
        }
    }
}
