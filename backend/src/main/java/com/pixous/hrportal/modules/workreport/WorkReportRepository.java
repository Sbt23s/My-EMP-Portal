package com.pixous.hrportal.modules.workreport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {

    List<WorkReport> findByUserIdOrderByWorkDateDescIdDesc(Long userId);

    List<WorkReport> findAllByOrderByWorkDateDescIdDesc();

    /** Everything filed for one day — used to see who has not filed anything. */
    List<WorkReport> findByWorkDate(java.time.LocalDate workDate);
}
