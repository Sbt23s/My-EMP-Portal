package com.pixous.hrportal.modules.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {
    List<Site> findByActiveTrueOrderByNameAsc();
    Optional<Site> findByCode(String code);
}
