package com.pixous.hrportal.modules.biometric;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BiometricEventRepository extends JpaRepository<BiometricEvent, Long> {

    /**
     * Whether this exact punch has already been recorded.
     *
     * <p>The same three columns as the unique key {@code uk_bio_event}. The
     * database is the guarantee — this is the cheap check that avoids throwing
     * a constraint violation on every one of Hikvision's retries, which default
     * to three.
     *
     * <p>Hikvision sends no idempotency key of its own, but a device's serial
     * number plus that device's own event counter plus the moment it occurred
     * identify one punch exactly.
     */
    Optional<BiometricEvent> findByDeviceSerialAndHikSerialNoAndOccurTime(
            String deviceSerial, Long hikSerialNo, LocalDateTime occurTime);

    /** The processor's queue, oldest first so a day is rebuilt in order. */
    List<BiometricEvent> findByProcessedFalseOrderByOccurTimeAsc(Pageable pageable);

    /**
     * One employee's punches within a window, in the order they happened.
     *
     * <p>Ordered by {@code occurTime} rather than by id: a device replaying its
     * offline buffer inserts old events after new ones, and reading them by
     * arrival would put an employee's evening punch before their morning one.
     */
    List<BiometricEvent> findByUserIdAndOccurTimeBetweenOrderByOccurTimeAsc(
            Long userId, LocalDateTime from, LocalDateTime to);

    /** The same, for a whole company — the HR activity stream and the reports. */
    List<BiometricEvent> findByCompanyIdAndOccurTimeBetweenOrderByOccurTimeDesc(
            Long companyId, LocalDateTime from, LocalDateTime to);

    /**
     * Punches nobody here owns.
     *
     * <p>Somebody authenticated on a terminal and no employee matched. Kept
     * rather than discarded, and surfaced so it can be fixed: usually an
     * employee number that was never set on the Hikvision side.
     */
    @Query("""
            select e from BiometricEvent e
            where e.userId is null
              and e.occurTime >= :from
            order by e.occurTime desc
            """)
    List<BiometricEvent> findUnmatchedSince(@Param("from") LocalDateTime from,
                                            Pageable pageable);

    /**
     * Whether any event at all has arrived for a company in a window.
     *
     * <p>The same question {@code WorkCalendar.attendanceWasKept} asks of the
     * register, for the same reason: a silent integration and a day when nobody
     * came in look identical in the data, and treating the first as the second
     * marks a whole company absent. Counted rather than fetched — only the
     * existence matters.
     */
    long countByCompanyIdAndOccurTimeBetween(Long companyId,
                                             LocalDateTime from, LocalDateTime to);

    long countByUserIdAndOccurTimeBetween(Long userId,
                                          LocalDateTime from, LocalDateTime to);
}
