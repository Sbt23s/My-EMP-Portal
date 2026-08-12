package com.pixous.hrportal.modules.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficeLocationRepository extends JpaRepository<OfficeLocation, Long> {
    List<OfficeLocation> findByActiveTrueOrderByNameAsc();
}
