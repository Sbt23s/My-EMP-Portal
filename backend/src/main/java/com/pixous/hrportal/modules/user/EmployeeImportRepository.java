package com.pixous.hrportal.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeImportRepository extends JpaRepository<EmployeeImport, Long> {

    /** Newest first, which is the order somebody looking for a mistake wants. */
    List<EmployeeImport> findAllByOrderByImportedAtDesc();
}
