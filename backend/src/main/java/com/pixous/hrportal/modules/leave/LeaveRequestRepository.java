package com.pixous.hrportal.modules.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Page<LeaveRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Manager inbox: pending requests raised by the manager's direct reports
    @Query("SELECT r FROM LeaveRequest r WHERE r.status = 'PENDING' AND r.userId IN :userIds ORDER BY r.createdAt ASC")
    List<LeaveRequest> findPendingForUsers(@Param("userIds") List<Long> userIds);

    List<LeaveRequest> findByUserIdAndStatus(Long userId, String status);
    List<LeaveRequest> findByUserId(Long userId);

    long countByStatus(String status);

    /**
     * Count a user's leave requests of a given type that fall in a calendar
     * month and still consume the monthly allowance (pending or approved).
     * Used to enforce the "1 casual + 1 sick per month" cap.
     */
    @Query("""
            SELECT COUNT(r) FROM LeaveRequest r
            WHERE r.userId = :userId
              AND r.leaveTypeId = :leaveTypeId
              AND r.status IN ('PENDING','APPROVED')
              AND YEAR(r.fromDate) = :year
              AND MONTH(r.fromDate) = :month
            """)
    long countMonthlyConsuming(@Param("userId") Long userId,
                               @Param("leaveTypeId") Long leaveTypeId,
                               @Param("year") int year,
                               @Param("month") int month);

    @Query("""
            SELECT COUNT(r) FROM LeaveRequest r
            WHERE r.userId = :userId
              AND r.leaveTypeId = :leaveTypeId
              AND r.status IN ('PENDING','APPROVED')
              AND r.fromDate >= :startDate AND r.fromDate <= :endDate
            """)
    long countRequestsInRange(@Param("userId") Long userId,
                              @Param("leaveTypeId") Long leaveTypeId,
                              @Param("startDate") java.time.LocalDate startDate,
                              @Param("endDate") java.time.LocalDate endDate);

    /**
     * The latest day of leave of this type the employee has taken or asked for,
     * so a minimum gap between two of them can be enforced. A rejected or
     * cancelled request does not count — nothing was taken.
     */
    @Query("""
            SELECT MAX(r.toDate) FROM LeaveRequest r
            WHERE r.userId = :userId
              AND r.leaveTypeId = :leaveTypeId
              AND r.status IN ('PENDING','APPROVED')
            """)
    java.time.LocalDate findLatestDayTaken(@Param("userId") Long userId,
                                           @Param("leaveTypeId") Long leaveTypeId);

    /** All pending requests across everyone (admin approval inbox). */
    @Query("SELECT r FROM LeaveRequest r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<LeaveRequest> findAllPending();

    List<LeaveRequest> findAllByOrderByCreatedAtDesc();

    /** Everyone currently on approved leave (the given date falls within from..to). */
    @Query("""
            SELECT r FROM LeaveRequest r
            WHERE r.status = 'APPROVED'
              AND r.fromDate <= :date AND r.toDate >= :date
            ORDER BY r.fromDate ASC
            """)
    List<LeaveRequest> findOnLeave(@Param("date") java.time.LocalDate date);

    /** All approved or pending leaves overlapping a date range (for the admin calendar). */
    @Query("""
            SELECT r FROM LeaveRequest r
            WHERE r.status IN ('APPROVED','PENDING')
              AND r.fromDate <= :to AND r.toDate >= :from
            ORDER BY r.fromDate ASC
            """)
    List<LeaveRequest> findInRange(@Param("from") java.time.LocalDate from,
                                  @Param("to") java.time.LocalDate to);
}
