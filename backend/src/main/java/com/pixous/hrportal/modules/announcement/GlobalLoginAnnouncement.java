package com.pixous.hrportal.modules.announcement;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "global_login_announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalLoginAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "media_type", nullable = false)
    private String mediaType; // VIDEO | IMAGE | POSTER

    @Column(name = "media_url", nullable = false, length = 1000)
    private String mediaUrl;

    @Column(name = "media_name")
    private String mediaName;

    @Column(name = "media_size")
    private Long mediaSize;

    /**
     * An optional Lottie animation played over the media when the popup opens.
     *
     * Separate from the media rather than replacing it: both are on screen at
     * once, the effect layered above. Null when none was uploaded.
     */
    @Column(name = "effect_url", length = 1000)
    private String effectUrl;
    @Column(name = "effect_name")
    private String effectName;
    @Column(name = "effect_size")
    private Long effectSize;

    /**
     * Whether to play it. Distinct from having one: an effect can be switched
     * off and back on without uploading it again.
     */
    @Column(name = "effect_enabled", nullable = false)
    private boolean effectEnabled;

    @Column(nullable = false)
    private String status; // ACTIVE | INACTIVE | DELETED

    @Column(name = "target_roles", nullable = false)
    private String targetRoles; // Comma separated: Employee,TL,HR,Admin

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.durationSeconds == null) {
            this.durationSeconds = 15;
        }
        if (this.status == null) {
            this.status = "INACTIVE";
        }
        if (this.targetRoles == null) {
            this.targetRoles = "Employee,TL,HR,Admin";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
