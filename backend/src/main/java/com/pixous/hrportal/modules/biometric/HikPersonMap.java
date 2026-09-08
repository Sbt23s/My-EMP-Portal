package com.pixous.hrportal.modules.biometric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Which person on the Hikvision terminal is which employee here.
 *
 * <p>This exists as a stored table rather than a lookup because the pushed
 * authentication event does not carry an employee number. Hikvision's
 * {@code IntelliInfo} (§A.3.106 of the V2.15.0 guide) gives {@code personId},
 * the names and the authentication result — and nothing this portal recognises.
 * The employee number, {@code personCode}, lives on the person APIs instead,
 * and {@code POST /persons/list} can only be filtered by name, email or phone,
 * so it cannot even be asked which person holds a given code.
 *
 * <p>So the mapping is built ahead of time by paging the whole person list and
 * kept here. An event then resolves in one indexed lookup with no call to
 * Hikvision on the hot path, which also keeps the integration clear of the
 * documented ceiling of five requests per second.
 */
@Getter
@Setter
@Entity
@Table(name = "hik_person_map")
public class HikPersonMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** {@code IntelliInfo.personId} — the only identifier a pushed event gives. */
    @Column(name = "hik_person_id", nullable = false, length = 64)
    private String hikPersonId;

    /**
     * {@code PersonInfo(1).personCode}, Hikvision's "Employee No.".
     *
     * <p>Nullable: a person can exist on the terminal with no employee number
     * set. That is the case an administrator needs to see, not one to hide
     * behind a failed insert.
     */
    @Column(name = "person_code", length = 32)
    private String personCode;

    /**
     * Whether a face and a fingerprint are enrolled, cached from
     * {@code /acspm/v1/maintain/overview/person/{id}/elementdetail} — its
     * {@code certificateStatusList} reports type 1 (fingerprint) and type 2
     * (face), with status 0 meaning applied.
     *
     * <p>A cache, not the truth; the device is. {@link #syncedAt} says how old
     * the answer is.
     */
    @Column(name = "face_enrolled", nullable = false)
    private boolean faceEnrolled = false;

    @Column(name = "fingerprint_enrolled", nullable = false)
    private boolean fingerprintEnrolled = false;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
