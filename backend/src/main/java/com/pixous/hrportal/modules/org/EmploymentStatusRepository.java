package com.pixous.hrportal.modules.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmploymentStatusRepository extends JpaRepository<EmploymentStatus, Long> {
    List<EmploymentStatus> findByActiveTrueOrderByNameAsc();
}
