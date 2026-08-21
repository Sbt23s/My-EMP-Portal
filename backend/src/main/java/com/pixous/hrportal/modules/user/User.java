package com.pixous.hrportal.modules.user;

import com.pixous.hrportal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * The central identity record. Login is by {@code aadhar} (preserving the
 * contract of the legacy PHP API) and the same row carries the full address
 * block, employment master-data foreign keys, and security/lifecycle state.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
@FilterDef(name = "tenantFilter", parameters = {@ParamDef(name = "companyId", type = Long.class)})
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class User extends BaseEntity {

    @Column(name = "company_id")
    private Long companyId;


    @Column(name = "employee_code", unique = true, length = 40)
    private String employeeCode;

    /** Login identifier. Replaces Aadhaar as the credential used to sign in. */
    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, length = 150)
    private String name;

    private LocalDate dob;

    @Column(name="gender")
    private Character gender;

    /** Optional profile detail. No longer used for authentication. */
    @Column(unique = true, length = 12)
    private String aadhar;

    @Column(unique = true, length = 15)
    private String phone;

    @Column(length = 150)
    private String email;

    // ----- extra profile detail (from the company's employee sheet) -----
    @Column(length = 20)
    private String pan;
    /** Provident fund account number. Optional — not everyone has one. */
    @Column(name = "pf_number", length = 30)
    private String pfNumber;
    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;
    @Column(name = "emergency_contact", length = 120)
    private String emergencyContact;
    @Column(name = "emergency_contact_relation", length = 60)
    private String emergencyContactRelation;
    @Column(name = "blood_group", length = 10)
    private String bloodGroup;
    @Column(name = "personal_email", length = 150)
    private String personalEmail;
    @Column(name = "designation_title", length = 150)
    private String designationTitle;
    @Column(name = "department_title", length = 150)
    private String departmentTitle;
    @Column(name = "position_title", length = 120)
    private String positionTitle;
    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private String techStack;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * The same password, encrypted so it can be read back for HR. Written
     * wherever the hash above is written. Null on accounts whose password was
     * last set before this existed.
     */
    @Column(name = "password_vault", length = 255)
    private String passwordVault;

    @Column(name = "photo_path", length = 255)
    private String photoPath;

    /**
     * The image behind the dashboard banner, chosen by the employee.
     *
     * Separate from the profile photo on purpose: one is the person, the other
     * is the backdrop, and replacing either should not disturb the other.
     */
    @Column(name = "cover_photo_path", length = 512)
    private String coverPhotoPath;

    /** Comma-separated upload paths: the employee's own paperwork. */
    @Column(columnDefinition = "text")
    private String documents;

    // ----- address block -----
    @Column(name = "care_of", length = 120)
    private String careOf;
    @Column(length = 120)
    private String house;
    @Column(length = 150)
    private String street;
    @Column(length = 150)
    private String locality;
    @Column(length = 120)
    private String vtc;
    @Column(length = 120)
    private String district;
    @Column(length = 120)
    private String state;
    @Column(length = 120)
    private String country = "India";
    @Column(length = 10)
    private String pincode;
    @Column(name = "post_office", length = 120)
    private String postOffice;

    // ----- employment (master-data FKs kept as ids for simplicity) -----
    @Column(name = "blood_group_id")
    private Long bloodGroupId;
    @Column(name = "department_id")
    private Long departmentId;
    @Column(name = "designation_id")
    private Long designationId;
    @Column(name = "office_location_id")
    private Long officeLocationId;
    @Column(name = "employment_status_id")
    private Long employmentStatusId;
    @Column(name = "position_id")
    private Long positionId;
    @Column(name = "reporting_manager_id")
    private Long reportingManagerId;
    @Column(name = "site_id")
    private Long siteId;
    @Column(name = "employment_type", length = 30)
    private String employmentType = "PERMANENT";

    /**
     * The Excel import that created this account, if one did. Null for everybody
     * added by hand — which is what keeps undoing an import from touching them.
     */
    @Column(name = "import_batch_id")
    private Long importBatchId;

    /**
     * One photo from the face enrolment, kept to be looked at.
     *
     * <p>The matching itself uses encodings held by the analytics service, from
     * which no picture can be recovered. This exists so that whoever registered
     * somebody else's face can check afterwards that they registered the right
     * person — which is the whole point of HR doing the registering.
     */
    @Column(name = "face_photo_path", length = 255)
    private String facePhotoPath;

    @Column(name = "face_registered_at")
    private LocalDateTime faceRegisteredAt;

    /** Registering somebody else's face is an act worth attributing. */
    @Column(name = "face_registered_by")
    private Long faceRegisteredBy;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;
    /**
     * When probation ends. Null means it has not been set, and the dashboard then
     * reads it as six months from the joining date.
     */
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;
    @Column(length = 20)
    private String industry = "IT";

    // ----- lifecycle + security -----
    @Column(name = "profile_status", length = 20)
    private String profileStatus = "PENDING";
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    /** When this person was last connected — the "last seen" behind chat presence. */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
