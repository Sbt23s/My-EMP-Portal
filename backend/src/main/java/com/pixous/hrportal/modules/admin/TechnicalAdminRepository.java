package com.pixous.hrportal.modules.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TechnicalAdminRepository extends JpaRepository<TechnicalAdmin, Long> {
    Optional<TechnicalAdmin> findByUsername(String username);
}
