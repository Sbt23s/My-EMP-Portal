package com.pixous.hrportal.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<Attendance> findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
            Long userId, LocalDate from, LocalDate to);

    List<Attendance> findByWorkDate(LocalDate workDate);

    long countByUserIdAndWorkDateBetweenAndStatus(
            Long userId, LocalDate from, LocalDate to, String status);
}
