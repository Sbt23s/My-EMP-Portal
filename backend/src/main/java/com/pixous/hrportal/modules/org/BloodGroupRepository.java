package com.pixous.hrportal.modules.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BloodGroupRepository extends JpaRepository<BloodGroup, Long> {
    List<BloodGroup> findByActiveTrueOrderByNameAsc();
}
