package com.pixous.hrportal.modules.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConfigOptionSetRepository extends JpaRepository<ConfigOptionSet, Long> {

    @Query("SELECT s FROM ConfigOptionSet s WHERE s.companyId IS NULL OR s.companyId = :companyId "
            + "ORDER BY s.module, s.name")
    List<ConfigOptionSet> findVisibleTo(@Param("companyId") Long companyId);

    /**
     * A company's own set first, the platform default second, so the caller can
     * take the head of the list and get the override when one exists.
     */
    @Query("SELECT s FROM ConfigOptionSet s WHERE s.setCode = :code "
            + "AND (s.companyId = :companyId OR s.companyId IS NULL) "
            + "ORDER BY CASE WHEN s.companyId IS NULL THEN 1 ELSE 0 END")
    List<ConfigOptionSet> findByCodeForCompany(@Param("code") String code,
                                               @Param("companyId") Long companyId);
}
