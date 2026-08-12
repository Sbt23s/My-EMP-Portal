package com.pixous.hrportal.modules.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    List<LeaveType> findByActiveTrueOrderByNameAsc();
    Optional<LeaveType> findByCodeIgnoreCase(String code);
}
