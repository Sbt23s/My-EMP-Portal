package com.pixous.hrportal.modules.biometric;

import com.pixous.hrportal.modules.attendance.Attendance;
import com.pixous.hrportal.modules.attendance.AttendanceRepository;
import com.pixous.hrportal.modules.attendance.AttendanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Turning stored punches into attendance rows.
 *
 * <p>This is the step that reaches the register payroll reads, so it is
 * deliberately the most conservative thing in the module. It only ever fills a
 * gap or extends a departure later; it does not delete rows, does not change a
 * status somebody set by hand, and never shortens a working day.
 *
 * <p>Kept apart from ingestion for two reasons. The webhook has five seconds to
 * answer and a slow decision inside it becomes a timeout and a redelivery. And
 * an event that cannot be interpreted today — one whose employee has not been
 * mapped yet — stays on the queue and is picked up once the mapping arrives,
 * instead of being lost because the only chance to read it was at the moment it
 * landed.
 */
@Slf4j
@Service
public class BiometricAttendanceProcessor {

    /**
     * How many events one pass will handle.
     *
     * <p>Bounded so a device that has been offline for a week, replaying
     * thousands of buffered punches, cannot occupy the scheduler indefinitely
     * or hold a transaction open across the whole backlog. The next pass takes
     * the next batch.
     */
    private static final int BATCH_SIZE = 500;

    private final BiometricEventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final BiometricAttendanceProcessor self;

    public BiometricAttendanceProcessor(BiometricEventRepository eventRepository,
                                        AttendanceRepository attendanceRepository,
                                        AttendanceService attendanceService,
                                        SimpMessagingTemplate messagingTemplate,
                                        @Lazy BiometricAttendanceProcessor self) {
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
        this.attendanceService = attendanceService;
        this.messagingTemplate = messagingTemplate;
        // Same reason as the ingest service: processOne is REQUIRES_NEW, and a
        // plain this. call skips the proxy, so the annotation would do nothing
        // and one bad event would roll back the whole batch.
        this.self = self;
    }

    /**
     * Processes whatever is waiting.
     *
     * <p>No transaction of its own — each event gets its own, so one that fails
     * is marked with its reason and the rest of the batch still lands.
     */
    public ProcessResult processPending() {
        List<BiometricEvent> pending = eventRepository
                .findByProcessedFalseOrderByOccurTimeAsc(PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return new ProcessResult(0, 0, 0);
        }

        int applied = 0;
        int skipped = 0;
        int failed = 0;

        for (BiometricEvent event : pending) {
            try {
                if (self.processOne(event.getId())) {
                    applied++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Could not process biometric event {}: {}", event.getId(), e.getMessage());
                self.markFailed(event.getId(), e.getMessage());
            }
        }

        ProcessResult result = new ProcessResult(applied, skipped, failed);
        if (applied > 0 || failed > 0) {
            log.info("Biometric attendance: {}", result.summary());
        }
        return result;
    }

    /**
     * One event.
     *
     * @return whether it changed an attendance row
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processOne(Long eventId) {
        BiometricEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.isProcessed()) {
            return false;
        }

        /*
         * An event we will never act on is marked done rather than left
         * pending. Otherwise the queue fills with rejected authentications and
         * card swipes that are re-read on every pass for ever, and the genuine
         * backlog is buried behind them.
         *
         * The row itself stays -- it is still evidence, and HR still needs to
         * see the run of failed face reads that means an enrolment is broken.
         */
        if (!HikEventTypes.isBiometricPunch(event.getEventType())
                || !event.isUsablePunch()) {
            markProcessed(event, null);
            return false;
        }

        LocalDate workDate = event.getOccurTime().toLocalDate();
        Attendance attendance = attendanceRepository
                .findByUserIdAndWorkDate(event.getUserId(), workDate)
                .orElse(null);

        boolean isNew = attendance == null;
        if (isNew) {
            attendance = new Attendance();
            attendance.setUserId(event.getUserId());
            attendance.setWorkDate(workDate);
            // A punch at a wall-mounted terminal is somebody physically at the
            // office, so the geofence question does not arise -- being at the
            // device is the proof the geofence exists to look for.
            attendance.setMode("OFFICE");
            attendance.setStatus("PRESENT");
            attendance.setWithinGeofence(true);
        }

        PunchDirection direction = PunchDirection.decide(
                event.getAttendanceStatus(),
                attendance.getPunchInAt(),
                attendance.getPunchOutAt());

        boolean changed = false;
        if (direction == PunchDirection.IN) {
            if (direction.shouldReplace(attendance.getPunchInAt(), event.getOccurTime())) {
                attendance.setPunchInAt(event.getOccurTime());
                int lateBy = attendanceService.lateMinutes(
                        attendance.getShiftId(), event.getOccurTime());
                attendance.setLateMinutes(lateBy);
                attendance.setLate(lateBy > 0);
                changed = true;
            }
        } else {
            if (direction.shouldReplace(attendance.getPunchOutAt(), event.getOccurTime())) {
                attendance.setPunchOutAt(event.getOccurTime());
                changed = true;
            }
        }

        if (changed) {
            recomputeDuration(attendance);
            /*
             * A day the terminal recorded is a day somebody was present, so a
             * row created by an earlier absence sweep stops saying ABSENT. Any
             * other status -- WFH, ON_LEAVE, something HR set deliberately -- is
             * left exactly as it is: a punch is evidence of presence, not
             * grounds to overrule a decision a person made.
             */
            if ("ABSENT".equals(attendance.getStatus())) {
                attendance.setStatus("PRESENT");
            }
            attendanceRepository.save(attendance);
        }

        event.setDirection(direction == PunchDirection.IN
                ? BiometricEvent.IN : BiometricEvent.OUT);
        markProcessed(event, null);

        if (changed) {
            announce(event, direction);
        }
        return changed;
    }

    /** Records why an event could not be processed, without losing the event. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long eventId, String reason) {
        eventRepository.findById(eventId).ifPresent(event -> {
            /*
             * Marked processed even though it failed, and the reason is kept on
             * the row. Leaving it pending would retry the same failure on every
             * pass for ever; the row still says what went wrong, so it can be
             * found and dealt with rather than retried blindly.
             */
            event.setProcessError(truncate(reason));
            markProcessed(event, truncate(reason));
            eventRepository.save(event);
        });
    }

    private void markProcessed(BiometricEvent event, String error) {
        event.setProcessed(true);
        event.setProcessedAt(LocalDateTime.now());
        if (error != null) {
            event.setProcessError(error);
        }
        eventRepository.save(event);
    }

    /**
     * Worked and overtime minutes, from the two punches.
     *
     * <p>Only when both are present. A day with an arrival and no departure
     * yet is the ordinary state of every morning, and writing a duration for it
     * would say somebody worked no time at all.
     */
    private void recomputeDuration(Attendance attendance) {
        LocalDateTime in = attendance.getPunchInAt();
        LocalDateTime out = attendance.getPunchOutAt();
        if (in == null || out == null) {
            return;
        }
        int worked = (int) Duration.between(in, out).toMinutes();
        attendance.setWorkedMinutes(Math.max(worked, 0));
        // The same rule the app's own punch-out uses, called rather than
        // copied, so overtime cannot come out differently by route.
        attendance.setOvertimeMinutes(attendanceService.overtimeMinutes(in, out));
    }

    /**
     * Tells the browser something happened.
     *
     * <p>The same {@code /topic/attendance} the app's own punches announce on,
     * so an open attendance screen updates whether the punch came from a phone
     * or from the wall.
     *
     * <p>Never allowed to fail the processing. The attendance row is the thing
     * that matters and it is already saved; a broken socket must not undo it
     * or leave the event unprocessed to be applied a second time.
     */
    private void announce(BiometricEvent event, PunchDirection direction) {
        try {
            messagingTemplate.convertAndSend("/topic/attendance", Map.of(
                    "kind", direction == PunchDirection.IN ? "PUNCH_IN" : "PUNCH_OUT",
                    "userId", event.getUserId(),
                    "source", "BIOMETRIC",
                    "method", event.getAuthMethod() == null ? "" : event.getAuthMethod(),
                    "at", event.getOccurTime().toString()));
        } catch (Exception e) {
            log.debug("Could not announce a biometric punch for user {}: {}",
                    event.getUserId(), e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /**
     * What one pass did.
     *
     * @param applied events that changed an attendance row
     * @param skipped events deliberately not acted on -- rejected
     *                authentications, card swipes, punches nobody owns
     * @param failed  events that threw
     */
    public record ProcessResult(int applied, int skipped, int failed) {
        public String summary() {
            return applied + " applied, " + skipped + " skipped, " + failed + " failed";
        }
    }
}
