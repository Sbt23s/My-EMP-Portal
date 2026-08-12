package com.pixous.hrportal.modules.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByActiveTrueOrderByNameAsc();
}
