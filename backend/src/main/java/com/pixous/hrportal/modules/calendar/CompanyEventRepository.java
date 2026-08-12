package com.pixous.hrportal.modules.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CompanyEventRepository extends JpaRepository<CompanyEvent, Long> {

    /**
     * Everything that touches the range, including a multi-day event that started
     * before it — a five-day training should still show on its last day.
     */
    @Query("""
            SELECT e FROM CompanyEvent e
            WHERE e.eventDate <= :to
              AND COALESCE(e.endDate, e.eventDate) >= :from
            ORDER BY e.eventDate ASC, e.startTime ASC
            """)
    List<CompanyEvent> findTouching(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<CompanyEvent> findAllByOrderByEventDateAsc();
}
