package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "technical_admins")
public class TechnicalAdmin extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    // Roles could be added here if there are different levels of tech admins,
    // but for now, we'll assume a single SUPER_ADMIN role for them.
}
