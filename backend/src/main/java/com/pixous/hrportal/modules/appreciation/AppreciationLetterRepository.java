package com.pixous.hrportal.modules.appreciation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppreciationLetterRepository extends JpaRepository<AppreciationLetter, Long> {

    /**
     * One employee's letters, newest first.
     *
     * <p>Drafts are left out: a letter nobody has sent is not yet a letter, and
     * showing somebody an appreciation still being drafted about them would be
     * worse than showing them nothing.
     */
    @Query("""
            SELECT a FROM AppreciationLetter a
            WHERE a.employeeId = :employeeId AND a.status <> 'DRAFT'
            ORDER BY a.letterDate DESC, a.id DESC
            """)
    List<AppreciationLetter> findSentFor(@Param("employeeId") Long employeeId);

    /** Everything, for whoever issues them. */
    List<AppreciationLetter> findAllByOrderByLetterDateDescIdDesc();

    /**
     * Highest reference code for a year prefix, or null.
     *
     * <p>The next code counts up from this rather than from the row count:
     * count()+1 regenerates a used code after any deletion, and the column is
     * unique, so the insert would fail.
     */
    @Query("SELECT MAX(a.referenceCode) FROM AppreciationLetter a WHERE a.referenceCode LIKE CONCAT(:prefix, '%')")
    String findMaxReferenceCode(@Param("prefix") String prefix);
}
