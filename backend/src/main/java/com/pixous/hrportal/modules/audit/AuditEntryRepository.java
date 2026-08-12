package com.pixous.hrportal.modules.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /**
     * The audit trail, filtered the ways somebody actually asks for it: a period,
     * a person, a category, or free text across the name and what was done.
     *
     * <p>Every filter is optional and skipped when null, so one query serves the
     * page rather than a combination of methods per filter.
     */
    @Query("""
            SELECT a FROM AuditEntry a
            WHERE a.at BETWEEN :from AND :to
              AND (:userId   IS NULL OR a.userId = :userId)
              AND (:category IS NULL OR a.category = :category)
              AND (:onlyFailures = FALSE OR a.succeeded = FALSE)
              AND (:q IS NULL OR LOWER(a.userName)   LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(a.summary)    LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(a.action)     LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(COALESCE(a.entityLabel, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(COALESCE(a.employeeCode, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY a.at DESC
            """)
    Page<AuditEntry> search(@Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("userId") Long userId,
                            @Param("category") String category,
                            @Param("onlyFailures") boolean onlyFailures,
                            @Param("q") String q,
                            Pageable pageable);

    /** Everything ever done to one record, for the history on that record. */
    List<AuditEntry> findByEntityTypeAndEntityIdOrderByAtDesc(String entityType, String entityId);

    /** How many of each category in a period, for the counts above the table. */
    @Query("""
            SELECT a.category, COUNT(a) FROM AuditEntry a
            WHERE a.at BETWEEN :from AND :to
            GROUP BY a.category ORDER BY COUNT(a) DESC
            """)
    List<Object[]> countByCategory(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** The busiest people in a period — who is doing most of the changing. */
    @Query("""
            SELECT a.userId, a.userName, a.employeeCode, COUNT(a) FROM AuditEntry a
            WHERE a.at BETWEEN :from AND :to AND a.userId IS NOT NULL
            GROUP BY a.userId, a.userName, a.employeeCode
            ORDER BY COUNT(a) DESC
            """)
    List<Object[]> countByUser(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByAtBetweenAndSucceededFalse(LocalDateTime from, LocalDateTime to);
}
