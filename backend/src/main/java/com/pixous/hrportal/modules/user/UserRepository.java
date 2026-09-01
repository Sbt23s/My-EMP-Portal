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

    /*
      Employee IDs are allocated against the table, not against the entity.

      User carries the tenant @Filter, so when that filter is active every
      query through the entity -- including the two above and below -- hides
      rows whose company_id does not match, and "company_id = x" is never
      true of a NULL. The unique index on users.employee_code has no such
      filter. Allocating from a view that cannot see part of the table, and
      then writing into an index that can, hands out an ID that already
      exists: the insert is refused and the person adding the employee gets
      "Something went wrong" with nothing to act on.

      Native, so what is checked is what the constraint will check.
    */
    @Query(value = "SELECT MAX(employee_code) FROM users WHERE employee_code LIKE CONCAT(:prefix, '%')",
           nativeQuery = true)
    String findMaxEmployeeCodeAcrossTenants(@Param("prefix") String prefix);

    @Query(value = "SELECT COUNT(*) FROM users WHERE UPPER(employee_code) = UPPER(:code)",
           nativeQuery = true)
    long countByEmployeeCodeAcrossTenants(@Param("code") String code);

    /*
      Username, Aadhaar and phone are unique across the whole users table, so
      the checks that guard them have to see the whole table as well.

      The derived queries above them (existsByUsername and friends) go through
      the User entity, which carries the tenant filter, so the moment an
      account has a company id those checks stop seeing rows outside it. The
      index does not, and the result is the failure that already happened once
      with employee_code: a duplicate passes the check, the insert is refused,
      and the person adding the employee is shown "Something went wrong"
      instead of "that username is taken".

      Nobody has a company id yet, so the filter is currently inactive and the
      old checks happen to work. That is luck, not design, and it runs out on
      the day multi-tenancy is switched on.
    */
    @Query(value = "SELECT COUNT(*) FROM users WHERE username = :username", nativeQuery = true)
    long countByUsernameAcrossTenants(@Param("username") String username);

    @Query(value = "SELECT COUNT(*) FROM users WHERE aadhar = :aadhar", nativeQuery = true)
    long countByAadharAcrossTenants(@Param("aadhar") String aadhar);

    @Query(value = "SELECT COUNT(*) FROM users WHERE phone = :phone", nativeQuery = true)
    long countByPhoneAcrossTenants(@Param("phone") String phone);

    /**
     * The account for a username, whichever company it belongs to.
     *
     * <p>Login has to be able to find an account before it knows which tenant
     * the person belongs to -- that is the answer, not the question. The
     * derived findByUsername carries the tenant filter, so as soon as the
     * filter is active it can only return a user from the company already in
     * context, and the sign-in of anyone outside it reads as "no such
     * username". Usernames are unique across every tenant (the index enforces
     * it, and createEmployee checks it across tenants), so resolving one
     * without the filter is exact, not a guess.
     *
     * <p>Native, for the same reason the count queries above are: it is the
     * one way to be certain the filter is not applied.
     */
    @Query(value = "SELECT * FROM users WHERE username = :username LIMIT 1", nativeQuery = true)
    Optional<User> findByUsernameAcrossTenants(@Param("username") String username);

    /** The same, by full name, for the name-based login fallback. */
    @Query(value = "SELECT * FROM users WHERE LOWER(name) = LOWER(:name)", nativeQuery = true)
    List<User> findByNameAcrossTenants(@Param("name") String name);

    /** Enabled users who hold the given permission code through any of their roles. */
    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roles r
            JOIN r.permissions p
            WHERE p.code = :permission AND u.enabled = true
            """)
    List<User> findByPermission(@Param("permission") String permission);
}
