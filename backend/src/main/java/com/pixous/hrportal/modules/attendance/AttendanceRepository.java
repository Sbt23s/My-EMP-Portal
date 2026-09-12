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

    /**
     * Every row for these people across a span of days, in one query.
     *
     * <p>The range report used to walk the span a day at a time, asking
     * findByWorkDate for each one and throwing away the people it had not asked
     * about in Java -- thirty-one queries for a month, each returning the whole
     * company's rows for that day. This asks the database the question that was
     * actually being asked, and lets the (work_date, user_id) index answer it.
     */
    List<Attendance> findByWorkDateBetweenAndUserIdIn(
            LocalDate from, LocalDate to, java.util.Collection<Long> userIds);

    long countByUserIdAndWorkDateBetweenAndStatus(
            Long userId, LocalDate from, LocalDate to, String status);

    /**
     * Rows of any status for one employee in a date range.
     *
     * <p>Answers "was attendance recorded at all", which payroll needs before
     * it can read a missing day as an absence. Counted rather than fetched
     * because only the existence matters.
     */
    long countByUserIdAndWorkDateBetween(Long userId, LocalDate from, LocalDate to);
}
