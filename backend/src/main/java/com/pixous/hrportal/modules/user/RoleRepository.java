package com.pixous.hrportal.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    Set<Role> findByCodeIn(Set<String> codes);
}
