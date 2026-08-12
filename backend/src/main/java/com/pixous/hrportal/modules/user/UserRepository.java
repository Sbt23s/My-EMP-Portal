package com.pixous.hrportal.modules.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAadhar(String aadhar);

    Optional<User> findByUsername(String username);

    /**
     * One user with their roles and those roles' permissions, in a single query.
     *
     * <p>Used by authentication, which runs on every request the portal makes.
     * Roles and permissions are both mapped EAGER, which sounds like it would
     * already be one query and is not: Hibernate fetches the user, then the roles,
     * then the permissions of each role separately. Against a database on the same
     * machine those extra round trips are invisible. Against the hosted database
     * they are a few hundred milliseconds each, and they were costing seconds per
     * page — the reason this exists.
     *
     * <p>A fetch join rather than a cache on purpose: an account that has just been
     * disabled, locked after failed logins, or offboarded must stop working on the
     * very next request, not when a cache entry expires.
     */
    @Query("select distinct u from User u "
            + "left join fetch u.roles r "
            + "left join fetch r.permissions "
            + "where u.id = :id")
    Optional<User> findByIdWithAuthorities(@Param("id") Long id);

    /** The same, by username, for the login itself. */
    @Query("select distinct u from User u "
            + "left join fetch u.roles r "
            + "left join fetch r.permissions "
            + "where u.username = :username")
    Optional<User> findByUsernameWithAuthorities(@Param("username") String username);

    /** Case-insensitive lookup by full name (used for name-based login; may be non-unique). */
    List<User> findByNameIgnoreCase(String name);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmployeeCode(String employeeCode);

    boolean existsByAadhar(String aadhar);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    List<User> findByReportingManagerId(Long managerId);

    /** Accounts created by one Excel import, for listing or undoing it. */
    List<User> findByImportBatchId(Long importBatchId);

    /** Guards against two people ending up with the same employee ID. */
    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);
    
    List<User> findByEnabledTrue();

    List<User> findByDesignationIdAndEnabledTrueOrderByNameAsc(Long designationId);

    /** Teammates matched by designation TITLE (case-insensitive) or, as a
     *  fallback, the numeric designation FK — so title-only members are included. */
    @Query("""
            SELECT u FROM User u
            WHERE u.enabled = true
              AND (
                    (:title IS NOT NULL AND LOWER(u.designationTitle) = LOWER(:title))
                 OR (:designationId IS NOT NULL AND u.designationId = :designationId)
              )
            ORDER BY u.name ASC
            """)
    List<User> findTeammatesByTitleOrDesignation(@Param("title") String title,
                                                 @Param("designationId") Long designationId);

    long countByReportingManagerId(Long managerId);

    long countByIndustry(String industry);

    long countByCompanyId(Long companyId);

    @Query("""
            SELECT u FROM User u
            WHERE (:q IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR u.aadhar LIKE CONCAT('%', :q, '%')
                   OR u.employeeCode LIKE CONCAT('%', :q, '%')
                   OR u.phone LIKE CONCAT('%', :q, '%'))
              AND (:industry IS NULL OR u.industry = :industry)
              AND (:departmentId IS NULL OR u.departmentId = :departmentId)
              AND (:status IS NULL
                   OR (:status = 'OFFBOARDED' AND u.profileStatus = 'OFFBOARDED')
                   OR (:status = 'ACTIVE' AND (u.profileStatus IS NULL OR u.profileStatus <> 'OFFBOARDED')))
            """)
    Page<User> search(@Param("q") String q,
                      @Param("industry") String industry,
                      @Param("departmentId") Long departmentId,
                      @Param("status") String status,
                      Pageable pageable);

    /**
     * The directory with the narrowing HR asks for: a team, a role, a department
     * and a joining-date window, any of them left out. The team is matched on the
     * title as well as the id, because an imported record carries the title only.
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN u.roles r
            WHERE (:q IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR u.aadhar LIKE CONCAT('%', :q, '%')
                   OR u.employeeCode LIKE CONCAT('%', :q, '%')
                   OR u.phone LIKE CONCAT('%', :q, '%'))
              AND (:industry IS NULL OR u.industry = :industry)
              AND (:departmentId IS NULL OR u.departmentId = :departmentId)
              AND (:designationId IS NULL OR u.designationId = :designationId)
              AND (:designationTitle IS NULL
                   OR LOWER(u.designationTitle) = LOWER(:designationTitle))
              AND (:roleCode IS NULL OR r.code = :roleCode)
              AND (:joinedFrom IS NULL OR u.dateOfJoining >= :joinedFrom)
              AND (:joinedTo IS NULL OR u.dateOfJoining <= :joinedTo)
              AND (:status IS NULL
                   OR (:status = 'OFFBOARDED' AND u.profileStatus = 'OFFBOARDED')
                   OR (:status = 'ACTIVE' AND (u.profileStatus IS NULL OR u.profileStatus <> 'OFFBOARDED')))
            """)
    Page<User> searchFiltered(@Param("q") String q,
                              @Param("industry") String industry,
                              @Param("departmentId") Long departmentId,
                              @Param("designationId") Long designationId,
                              @Param("designationTitle") String designationTitle,
                              @Param("roleCode") String roleCode,
                              @Param("joinedFrom") java.time.LocalDate joinedFrom,
                              @Param("joinedTo") java.time.LocalDate joinedTo,
                              @Param("status") String status,
                              Pageable pageable);

    @Query("SELECT MAX(u.employeeCode) FROM User u WHERE u.employeeCode LIKE CONCAT(:prefix, '%')")
    String findMaxEmployeeCode(@Param("prefix") String prefix);

    /** Enabled users who hold the given permission code through any of their roles. */
    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roles r
            JOIN r.permissions p
            WHERE p.code = :permission AND u.enabled = true
            """)
    List<User> findByPermission(@Param("permission") String permission);
}
