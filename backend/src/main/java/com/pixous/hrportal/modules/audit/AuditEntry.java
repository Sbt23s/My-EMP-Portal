package com.pixous.hrportal.modules.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One recorded action: who did it, what it was, and where from.
 *
 * <p>Mapped onto the audit_log table that already existed, so the original column
 * names are kept — actor_id, details, created_at — rather than renamed to suit
 * this class. The alternative was a migration that rewrites a table somebody may
 * yet be reading.
 *
 * <p>The person's name and code are copied in rather than joined. An audit row has
 * to still make sense after the account it refers to has been deleted, which is
 * exactly the moment somebody comes looking for it.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime at = LocalDateTime.now();

    @Column(name = "actor_id")
    private Long userId;

    @Column(name = "user_name", length = 150)
    private String userName;

    @Column(name = "employee_code", length = 30)
    private String employeeCode;

    @Column(name = "roles", length = 255)
    private String roles;

    /** PAYROLL, EMPLOYEE, ATTENDANCE, LEAVE, FACE, CHAT, SECURITY, SYSTEM. */
    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 120)
    private String action;

    /** The sentence a person reads. */
    @Column(length = 500)
    private String summary;

    @Column(name = "entity_type", length = 80)
    private String entityType;

    @Column(name = "entity_id", length = 60)
    private String entityId;

    @Column(name = "entity_label", length = 200)
    private String entityLabel;

    /** Before and after, for the changes where that is worth keeping. */
    @Column(name = "details", columnDefinition = "TEXT")
    private String detail;

    @Column(length = 10)
    private String method;

    @Column(length = 400)
    private String path;

    private Integer status;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String device;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(nullable = false)
    private boolean succeeded = true;
}
