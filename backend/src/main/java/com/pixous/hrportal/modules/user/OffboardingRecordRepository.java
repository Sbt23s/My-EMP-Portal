package com.pixous.hrportal.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OffboardingRecordRepository extends JpaRepository<OffboardingRecord, Long> {
    Optional<OffboardingRecord> findByUserId(Long userId);

    /**
     * Every offboarding record for a set of people, in one query.
     *
     * <p>For the places that need the relieving date of a group -- the
     * dashboard's twelve-month exit chart, the service record -- where calling
     * findByUserId per person is one statement per leaver.
     */
    List<OffboardingRecord> findByUserIdIn(Collection<Long> userIds);
}
