package com.pixous.hrportal.modules.biometric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HikPersonMapRepository extends JpaRepository<HikPersonMap, Long> {

    /**
     * The lookup a pushed event performs.
     *
     * <p>The hot path. {@code personId} is all {@code IntelliInfo} gives us, and
     * this resolves it to an employee without calling Hikvision — which matters,
     * because the documented request ceiling is five per second and a busy
     * terminal at nine in the morning exceeds that on its own.
     */
    Optional<HikPersonMap> findByHikPersonId(String hikPersonId);

    Optional<HikPersonMap> findByUserId(Long userId);

    /** Reconciliation reads by employee number, which is what the two sides share. */
    Optional<HikPersonMap> findByPersonCode(String personCode);

    List<HikPersonMap> findByCompanyId(Long companyId);

    /**
     * Which employees have no terminal identity yet.
     *
     * <p>Answered from the mapping side rather than by scanning users, so the
     * caller compares this against its own employee list. The screen that needs
     * it is "who cannot punch", and an employee missing from here is exactly
     * that person.
     */
    List<HikPersonMap> findByCompanyIdAndPersonCodeIsNull(Long companyId);

    /** Every mapping, newest first, for the screen that shows them all. */
    List<HikPersonMap> findAllByOrderByIdDesc();
}
