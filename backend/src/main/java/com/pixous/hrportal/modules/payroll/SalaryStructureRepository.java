package com.pixous.hrportal.modules.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {
    Optional<SalaryStructure> findByUserIdAndActiveTrue(Long userId);

    /**
     * The salary that was in force on a given date.
     *
     * <p>A payslip has to be built from the structure that applied to the
     * month it covers, not the one that applies today. Raising somebody in
     * October and then regenerating September would otherwise pay the
     * October figure for a month worked at the old one -- and a historical
     * payslip that changes when nothing about that month changed is worse
     * than one that is merely wrong.
     *
     * <p>Newest first, so the most recent structure that had already taken
     * effect wins. A row with no effective date is treated as always having
     * applied: that is what it meant before the column was filled in.
     */
    @Query("""
            SELECT s FROM SalaryStructure s
            WHERE s.userId = :userId
              AND (s.effectiveFrom IS NULL OR s.effectiveFrom <= :onDate)
            ORDER BY s.effectiveFrom DESC, s.id DESC
            """)
    List<SalaryStructure> findEffectiveOn(@Param("userId") Long userId,
                                          @Param("onDate") java.time.LocalDate onDate);

    List<SalaryStructure> findByActiveTrue();
}
