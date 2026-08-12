package com.pixous.hrportal.modules.attendance;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.config.AppProperties;
import com.pixous.hrportal.modules.attendance.dto.AttendanceResponse;
import com.pixous.hrportal.modules.attendance.dto.AttendanceSummary;
import com.pixous.hrportal.modules.attendance.dto.PunchRequest;
import com.pixous.hrportal.modules.org.OfficeLocation;
import com.pixous.hrportal.modules.org.OfficeLocationRepository;
import com.pixous.hrportal.modules.org.Shift;
import com.pixous.hrportal.modules.org.ShiftRepository;
import com.pixous.hrportal.modules.org.Site;
import com.pixous.hrportal.modules.org.SiteRepository;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Punch-in / punch-out with GPS geofence validation, late detection and
 * overtime calculation. WFH punches skip the geofence; office/site punches are
 * validated against the relevant location radius and flagged if outside.
 */
@Service
public class AttendanceService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final SiteRepository siteRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final ShiftRepository shiftRepository;
    private final GeofenceService geofenceService;
    private final AppProperties props;
    private final LeaveRequestRepository leaveRequestRepository;
    private final com.pixous.hrportal.modules.org.HolidayRepository holidayRepository;
    private final com.pixous.hrportal.common.StorageService storageService;
    private final jakarta.persistence.EntityManager entityManager;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    /**
     * Tells every open screen that somebody punched.
     *
     * <p>The dashboard's attendance figures were read once when the page loaded and
     * then never again: somebody arriving at nine did not appear until whoever was
     * watching happened to reload. This is what makes the counts move on their own.
     *
     * <p>Deliberately carries almost nothing — the punch is not sent over the socket.
     * Each screen re-reads its own figures, so nobody receives attendance for
     * employees they have no business seeing, and a payload that grows later cannot
     * quietly start leaking.
     *
     * <p>Failure is swallowed. A broker that is not there must never turn a
     * successful punch into a failed one; the worst case is a dashboard that waits
     * for its next scheduled refresh, which is where it started.
     */
    private void announcePunch(String kind, Long userId) {
        try {
            messagingTemplate.convertAndSend("/topic/attendance", java.util.Map.of(
                    "kind", kind,
                    "userId", userId == null ? 0L : userId,
                    "at", java.time.LocalDateTime.now().toString()));
        } catch (Exception e) {
            log.debug("Could not announce {} for user {}: {}", kind, userId, e.getMessage());
        }
    }

    public AttendanceService(AttendanceRepository attendanceRepository,
                             UserRepository userRepository,
                             SiteRepository siteRepository,
                             OfficeLocationRepository officeLocationRepository,
                             ShiftRepository shiftRepository,
                             GeofenceService geofenceService,
                             AppProperties props,
                             LeaveRequestRepository leaveRequestRepository,
                             com.pixous.hrportal.modules.org.HolidayRepository holidayRepository,
                             com.pixous.hrportal.common.StorageService storageService,
                             jakarta.persistence.EntityManager entityManager,
                             org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.storageService = storageService;
        this.entityManager = entityManager;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.shiftRepository = shiftRepository;
        this.geofenceService = geofenceService;
        this.props = props;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayRepository = holidayRepository;
    }

    /**
     * Punch-in status today for the caller's own team (same designation).
     * Scoped to the caller's team so any employee may read it.
     */
    @Transactional(readOnly = true)
    public List<com.pixous.hrportal.modules.attendance.dto.TeamPresenceEntry> myTeamPresenceToday(Long userId) {
        User me = userRepository.findById(userId).orElse(null);
        if (me == null) return List.of();
        String title = me.getDesignationTitle() == null ? "" : me.getDesignationTitle().trim();

        List<User> teammates = title.isEmpty()
                ? List.of(me)
                : userRepository.findTeammatesByTitleOrDesignation(title, me.getDesignationId());
        if (teammates.isEmpty()) teammates = List.of(me);

        LocalDate today = LocalDate.now();
        Map<Long, Attendance> byUser = attendanceRepository.findByWorkDate(today).stream()
                .collect(Collectors.toMap(Attendance::getUserId, a -> a, (a, b) -> a));

        return teammates.stream()
                .map(u -> {
                    Attendance a = byUser.get(u.getId());
                    boolean in = a != null && a.getPunchInAt() != null;
                    return new com.pixous.hrportal.modules.attendance.dto.TeamPresenceEntry(
                            u.getId(), in, in ? a.getPunchInAt() : null);
                })
                .toList();
    }

    /**
     * Everyone absent today: active/onboarding employees who neither punched
     * in nor are on approved/pending leave. Reflects actual punch/leave data
     * regardless of weekday (some teams work Saturdays). Visible to every
     * employee (used by the dashboard "Absent today" widget).
     */
    @Transactional(readOnly = true)
    public List<com.pixous.hrportal.modules.attendance.dto.TodayStatusEntry> todayAbsentees() {
        LocalDate today = LocalDate.now();
        Set<Long> punchedIn = attendanceRepository.findByWorkDate(today).stream()
                .filter(a -> a.getPunchInAt() != null)
                .map(Attendance::getUserId)
                .collect(Collectors.toSet());
        Set<Long> onLeave = leaveRequestRepository.findOnLeave(today).stream()
                .map(com.pixous.hrportal.modules.leave.LeaveRequest::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                .filter(u -> !punchedIn.contains(u.getId()) && !onLeave.contains(u.getId()))
                .map(u -> new com.pixous.hrportal.modules.attendance.dto.TodayStatusEntry(
                        u.getId(), u.getName(), u.getEmployeeCode(), u.getDesignationTitle()))
                .toList();
    }

    @Transactional
    public AttendanceResponse punchIn(Long userId, PunchRequest req) {
        LocalDate today = LocalDate.now();
        attendanceRepository.findByUserIdAndWorkDate(userId, today).ifPresent(a -> {
            if (a.getPunchInAt() != null) {
                throw ApiException.business("You have already punched in today");
            }
        });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        String mode = req.mode() == null ? "OFFICE" : req.mode().toUpperCase();
        Attendance attendance = attendanceRepository.findByUserIdAndWorkDate(userId, today)
                .orElseGet(Attendance::new);
        attendance.setUserId(userId);
        attendance.setWorkDate(today);
        attendance.setPunchInAt(LocalDateTime.now());
        attendance.setMode(mode);
        attendance.setInLatitude(req.latitude());
        attendance.setInLongitude(req.longitude());
        attendance.setShiftId(req.shiftId());

        if ("WFH".equals(mode)) {
            attendance.setStatus("WFH");
            attendance.setWithinGeofence(null);
        } else {
            evaluateGeofence(attendance, user, req, mode);
            attendance.setStatus("PRESENT");
        }

        int lateBy = lateMinutes(req.shiftId(), attendance.getPunchInAt());
        attendance.setLateMinutes(lateBy);
        attendance.setLate(lateBy > 0);
        attendanceRepository.save(attendance);
        announcePunch("PUNCH_IN", userId);
        return toResponse(attendance);
    }

    @Transactional
    public AttendanceResponse punchOut(Long userId, PunchRequest req) {
        LocalDate today = LocalDate.now();
        Attendance attendance = attendanceRepository.findByUserIdAndWorkDate(userId, today)
                .orElseThrow(() -> ApiException.business("Punch-in not found for today"));
        if (attendance.getPunchInAt() == null) {
            throw ApiException.business("You must punch in before punching out");
        }
        if (attendance.getPunchOutAt() != null) {
            throw ApiException.business("You have already punched out today");
        }

        attendance.setPunchOutAt(LocalDateTime.now());
        attendance.setOutLatitude(req.latitude());
        attendance.setOutLongitude(req.longitude());

        int worked = (int) Duration.between(attendance.getPunchInAt(),
                attendance.getPunchOutAt()).toMinutes();
        attendance.setWorkedMinutes(Math.max(worked, 0));

        attendance.setOvertimeMinutes(overtimeMinutes(attendance.getPunchInAt(),
                attendance.getPunchOutAt()));

        attendanceRepository.save(attendance);
        announcePunch("PUNCH_OUT", userId);
        return toResponse(attendance);
    }

    @Transactional(readOnly = true)
    public AttendanceResponse today(Long userId) {
        return attendanceRepository.findByUserIdAndWorkDate(userId, LocalDate.now())
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> myCalendar(Long userId, LocalDate from, LocalDate to) {
        return attendanceRepository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(userId, from, to)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AttendanceSummary summary(Long userId, int month, int year) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<Attendance> records = attendanceRepository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(userId, from, to);
        long present = records.stream().filter(a -> "PRESENT".equals(a.getStatus())).count();
        long wfh = records.stream().filter(a -> "WFH".equals(a.getStatus())).count();
        long late = records.stream().filter(Attendance::isLate).count();
        int overtime = records.stream()
                .mapToInt(a -> a.getOvertimeMinutes() == null ? 0 : a.getOvertimeMinutes()).sum();
        int lateMinutesTotal = records.stream().mapToInt(Attendance::getLateMinutes).sum();

        // Count only days that were actually worked days: Sunday is the one
        // weekly off and holidays do not count either. Counting every calendar
        // day made absences include Sundays, so a full month showed four or five
        // absences nobody had.
        LocalDate today = LocalDate.now();
        LocalDate countTo = today.isBefore(to) ? today : to;
        int workingDays = 0;
        if (!countTo.isBefore(from)) {
            Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, countTo)
                    .stream().map(com.pixous.hrportal.modules.org.Holiday::getHolidayDate)
                    .collect(Collectors.toSet());
            for (LocalDate d = from; !d.isAfter(countTo); d = d.plusDays(1)) {
                if (d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) continue;
                if (holidays.contains(d)) continue;
                workingDays++;
            }
        }
        long absent = Math.max(0, workingDays - present - wfh);
        return new AttendanceSummary(month, year, present, wfh, late, absent,
                overtime, lateMinutesTotal, workingDays);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> teamForDate(List<Long> memberIds, LocalDate date) {
        return attendanceRepository.findByWorkDate(date).stream()
                .filter(a -> memberIds.contains(a.getUserId()))
                .map(this::toResponse)
                .toList();
    }

    /** The same rows as teamForDate, across a range â€” for downloadable reports. */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> teamForRange(List<Long> memberIds, LocalDate from, LocalDate to) {
        java.util.Set<Long> ids = new java.util.HashSet<>(memberIds);
        List<AttendanceResponse> out = new java.util.ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            attendanceRepository.findByWorkDate(d).stream()
                    .filter(a -> ids.contains(a.getUserId()))
                    .map(this::toResponse)
                    .forEach(out::add);
        }
        return out;
    }

    // ---- helpers ----

    private void evaluateGeofence(Attendance attendance, User user, PunchRequest req, String mode) {
        boolean within = false;
        // No GPS supplied (e.g. desk staff / location off): record as outside the
        // geofence but never block or crash the punch.
        boolean hasCoords = req.latitude() != null && req.longitude() != null;
        if (!hasCoords) {
            attendance.setWithinGeofence(false);
            attendance.setGeofenceException(true);
            return;
        }
        if ("SITE".equals(mode)) {
            Long siteId = req.siteId() != null ? req.siteId() : user.getSiteId();
            if (siteId != null) {
                Site site = siteRepository.findById(siteId).orElse(null);
                if (site != null) {
                    attendance.setSiteId(site.getId());
                    int radius = site.getGeofenceRadiusMetres() == null
                            ? props.attendance().defaultGeofenceRadiusMetres()
                            : site.getGeofenceRadiusMetres();
                    within = geofenceService.isWithin(req.latitude(), req.longitude(),
                            site.getLatitude(), site.getLongitude(), radius);
                }
            }
        } else { // OFFICE / BIOMETRIC
            Long locId = req.officeLocationId() != null
                    ? req.officeLocationId() : user.getOfficeLocationId();
            if (locId != null) {
                OfficeLocation loc = officeLocationRepository.findById(locId).orElse(null);
                if (loc != null) {
                    int radius = loc.getGeofenceRadiusMetres() == null
                            ? props.attendance().defaultGeofenceRadiusMetres()
                            : loc.getGeofenceRadiusMetres();
                    within = geofenceService.isWithin(req.latitude(), req.longitude(),
                            loc.getLatitude(), loc.getLongitude(), radius);
                }
            }
        }
        attendance.setWithinGeofence(within);
        attendance.setGeofenceException(!within);
    }

    /** Office start for a punch: the assigned shift's, or the configured default. */
    private java.time.LocalTime startTimeFor(Long shiftId) {
        if (shiftId != null) {
            Shift shift = shiftRepository.findById(shiftId).orElse(null);
            if (shift != null && shift.getStartTime() != null) {
                return shift.getStartTime();
            }
        }
        return parseTime(props.attendance().officeStart(), java.time.LocalTime.of(9, 0));
    }

    private static java.time.LocalTime parseTime(String raw, java.time.LocalTime fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return java.time.LocalTime.parse(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * How many minutes past the office start this punch was, or 0 when on time.
     *
     * Previously this keyed off the shift alone and returned false whenever no
     * shift was supplied â€” which the punch endpoints never do â€” so nobody was
     * ever marked late. It now falls back to the configured office start.
     */
    private int lateMinutes(Long shiftId, LocalDateTime punchInAt) {
        if (punchInAt == null) return 0;
        LocalDateTime allowedUntil = punchInAt.toLocalDate()
                .atTime(startTimeFor(shiftId))
                .plusMinutes(props.attendance().lateGraceMinutes());
        if (!punchInAt.isAfter(allowedUntil)) return 0;
        return (int) Duration.between(allowedUntil, punchInAt).toMinutes();
    }

    /** Minutes worked past the office end time â€” nothing before it counts. */
    private int overtimeMinutes(LocalDateTime punchInAt, LocalDateTime punchOutAt) {
        if (punchInAt == null || punchOutAt == null) return 0;
        LocalDateTime officeEnd = punchOutAt.toLocalDate()
                .atTime(parseTime(props.attendance().officeEnd(), java.time.LocalTime.of(18, 0)));
        if (!punchOutAt.isAfter(officeEnd)) return 0;
        // Someone who started after the office end still only earns from when
        // they actually began.
        LocalDateTime from = punchInAt.isAfter(officeEnd) ? punchInAt : officeEnd;
        return (int) Math.max(0, Duration.between(from, punchOutAt).toMinutes());
    }

    private AttendanceResponse toResponse(Attendance a) {
        Placed in = place(a.getInLatitude(), a.getInLongitude());
        Placed out = place(a.getOutLatitude(), a.getOutLongitude());
        return new AttendanceResponse(a.getId(), a.getUserId(), a.getWorkDate(),
                a.getPunchInAt(), a.getPunchOutAt(), a.getMode(), a.getStatus(),
                a.isLate(), a.getLateMinutes(), a.getWithinGeofence(), a.isGeofenceException(),
                a.getWorkedMinutes(), a.getOvertimeMinutes(),
                a.getInLatitude(), a.getInLongitude(),
                a.getOutLatitude(), a.getOutLongitude(),
                in.name, out.name, in.distance, a.getInAccuracyM(),
                a.isFaceVerified(), a.getFacePhotoPath(), a.getFaceScore(),
                a.isOutFaceVerified(), a.getOutFacePhotoPath(), a.getInDevice());
    }

    /** A named place, and how far away the nearest one was when none contained it. */
    private record Placed(String name, Integer distance) {
        static final Placed NOWHERE = new Placed(null, null);
    }

    /**
     * Names the place a punch was made from.
     *
     * <p>Coordinates are true and useless to read: nobody looking at a timesheet
     * can tell whether 12.97610, 80.22140 is the office. The same numbers are
     * matched against every office and site on record, using the radius each one
     * carries, so a punch inside one is labelled with its name â€” and a punch
     * outside all of them says "Other location" with the distance to the nearest,
     * which is the part that decides whether it needs asking about.
     */
    private Placed place(java.math.BigDecimal lat, java.math.BigDecimal lng) {
        if (lat == null || lng == null) return Placed.NOWHERE;

        String nearestName = null;
        double nearest = Double.MAX_VALUE;

        for (OfficeLocation o : officeLocationRepository.findAll()) {
            if (o.getLatitude() == null || o.getLongitude() == null) continue;
            double d = geofenceService.distanceMetres(lat.doubleValue(), lng.doubleValue(),
                    o.getLatitude().doubleValue(), o.getLongitude().doubleValue());
            int radius = o.getGeofenceRadiusMetres() == null
                    ? props.attendance().defaultGeofenceRadiusMetres()
                    : o.getGeofenceRadiusMetres();
            if (d <= radius) return new Placed(o.getName(), (int) Math.round(d));
            if (d < nearest) { nearest = d; nearestName = o.getName(); }
        }

        for (Site s : siteRepository.findAll()) {
            if (s.getLatitude() == null || s.getLongitude() == null) continue;
            double d = geofenceService.distanceMetres(lat.doubleValue(), lng.doubleValue(),
                    s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
            int radius = s.getGeofenceRadiusMetres() == null
                    ? props.attendance().defaultGeofenceRadiusMetres()
                    : s.getGeofenceRadiusMetres();
            if (d <= radius) return new Placed(s.getName(), (int) Math.round(d));
            if (d < nearest) { nearest = d; nearestName = s.getName(); }
        }

        // Outside everything known. Saying which was closest and by how far is what
        // turns "somewhere else" into something an admin can act on.
        if (nearestName != null && nearest < 100_000) {
            return new Placed("Other location â€” " + formatDistance(nearest) + " from " + nearestName,
                    (int) Math.round(nearest));
        }
        // Nothing on record to compare against, so the honest answer is that this
        // is somewhere unrecognised rather than that it is wrong.
        return new Placed("Other location", null);
    }

    private static String formatDistance(double metres) {
        return metres < 1000
                ? Math.round(metres) + " m"
                : String.format("%.1f km", metres / 1000.0);
    }

    // ---------------------------------------------------------------------
    // Face-verified punching
    // ---------------------------------------------------------------------

    /**
     * Punches in or out, recording that the face was verified and keeping the
     * selfie it was verified from.
     *
     * <p>Verification happens in the analytics service, which holds the
     * enrolments; this records its verdict. A failed verification does not refuse
     * the punch â€” somebody whose camera or enrolment lets them down still has to
     * be able to mark attendance â€” it is stored as unverified, and HR can see
     * exactly which punches those were.
     */
    @Transactional
    public AttendanceResponse facePunch(Long userId, boolean punchIn, PunchRequest req,
                                        boolean verified, java.math.BigDecimal score,
                                        String detail,
                                        org.springframework.web.multipart.MultipartFile photo,
                                        Integer accuracyMetres, String userAgent) {
        // Refused, not recorded as unverified. The rule the company asked for is
        // that a punch means a verified face â€” enforced here rather than only in
        // the browser, because a request can be made without one.
        if (!verified) {
            throw ApiException.business(
                    "Your face was not verified, so the punch was not recorded. Try again facing "
                    + "the camera in good light. If it keeps failing, ask HR to register your face again.");
        }

        // The punch itself goes through the ordinary path, so the geofence, late
        // detection and overtime rules are the same ones as always.
        AttendanceResponse response = punchIn ? punchIn(userId, req) : punchOut(userId, req);

        Attendance attendance = attendanceRepository
                .findByUserIdAndWorkDate(userId, LocalDate.now())
                .orElse(null);
        if (attendance == null) return response;

        String path = null;
        if (photo != null && !photo.isEmpty()) {
            try {
                path = storageService.store(photo, "attendance-face");
            } catch (Exception e) {
                // The photo is evidence, not the punch. Losing it must not lose
                // somebody their attendance for the day.
                path = null;
            }
        }

        // Truncated rather than dropped: an unusually long report should cost the
        // tail of the detail, not the record that a check happened.
        String trimmed = detail == null ? null
                : detail.length() > 60_000 ? detail.substring(0, 60_000) : detail;
        String device = userAgent == null ? null
                : userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;

        if (punchIn) {
            attendance.setFaceVerified(verified);
            attendance.setFaceScore(score);
            attendance.setFaceDetail(trimmed);
            attendance.setInAccuracyM(accuracyMetres);
            attendance.setInDevice(device);
            if (path != null) attendance.setFacePhotoPath(path);
        } else {
            attendance.setOutFaceVerified(verified);
            attendance.setOutFaceScore(score);
            attendance.setOutFaceDetail(trimmed);
            attendance.setOutAccuracyM(accuracyMetres);
            attendance.setOutDevice(device);
            if (path != null) attendance.setOutFacePhotoPath(path);
        }
        attendance.setUpdatedAt(LocalDateTime.now());
        attendanceRepository.save(attendance);
        announcePunch(punchIn ? "PUNCH_IN" : "PUNCH_OUT", userId);

        return toResponse(attendance);
    }

    // ---------------------------------------------------------------------
    // What a day actually consisted of
    // ---------------------------------------------------------------------

    /**
     * One person's punch, where it was made from, whether the face matched, and
     * the work recorded against that day. Attendance on its own says somebody was
     * present; this says what being present amounted to.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> dayDetail(Long userId, LocalDate date, Long requesterId) {
        boolean self = userId.equals(requesterId);
        if (!self && !canSeeOthers(requesterId)) {
            throw ApiException.business("You can only look at your own day.");
        }

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        out.put("userId", userId);
        out.put("name", user.getName());
        out.put("employeeCode", user.getEmployeeCode());
        out.put("team", user.getDesignationTitle());
        out.put("date", date.toString());

        Attendance a = attendanceRepository.findByUserIdAndWorkDate(userId, date).orElse(null);
        Map<String, Object> punch = new java.util.LinkedHashMap<>();
        if (a == null) {
            punch.put("present", false);
        } else {
            punch.put("present", a.getPunchInAt() != null);
            punch.put("punchInAt", a.getPunchInAt());
            punch.put("punchOutAt", a.getPunchOutAt());
            punch.put("mode", a.getMode());
            punch.put("status", a.getStatus());
            punch.put("late", a.isLate());
            punch.put("lateMinutes", a.getLateMinutes());
            punch.put("workedMinutes", a.getWorkedMinutes());
            punch.put("overtimeMinutes", a.getOvertimeMinutes());
            punch.put("withinGeofence", a.getWithinGeofence());
            punch.put("inLatitude", a.getInLatitude());
            punch.put("inLongitude", a.getInLongitude());
            punch.put("outLatitude", a.getOutLatitude());
            punch.put("outLongitude", a.getOutLongitude());
            punch.put("faceVerified", a.isFaceVerified());
            punch.put("facePhotoPath", a.getFacePhotoPath());
            punch.put("faceScore", a.getFaceScore());
            punch.put("faceDetail", a.getFaceDetail());
            punch.put("inAccuracyM", a.getInAccuracyM());
            punch.put("inDevice", a.getInDevice());
            punch.put("outFaceVerified", a.isOutFaceVerified());
            punch.put("outFacePhotoPath", a.getOutFacePhotoPath());
            punch.put("outFaceScore", a.getOutFaceScore());
            punch.put("outFaceDetail", a.getOutFaceDetail());
            punch.put("outAccuracyM", a.getOutAccuracyM());
            punch.put("outDevice", a.getOutDevice());
        }
        out.put("punch", punch);

        // Tasks this person was carrying on that day: raised by then, and either
        // still open or finished/due that day. `tasks` records completed_at rather
        // than a general updated_at, so that is what dates a finished one.
        out.put("tasks", hasColumns("tasks", "assigned_to", "completed_at") ? rows("""
                SELECT id, title, status, priority, progress, due_date, completed_at
                FROM tasks
                WHERE assigned_to = :uid
                  AND DATE(created_at) <= :d
                  AND (status <> 'COMPLETED'
                       OR DATE(completed_at) = :d
                       OR due_date = :d)
                ORDER BY FIELD(status,'IN_PROGRESS','TODO','COMPLETED'), due_date
                LIMIT 25
                """, Map.of("uid", userId, "d", date),
                "id", "title", "status", "priority", "progress", "dueDate", "completedAt")
                : List.of());

        out.put("workReports", hasColumns("work_reports", "task_description", "work_hours") ? rows("""
                SELECT id, work_date, project_name, task_description, work_hours
                FROM work_reports
                WHERE user_id = :uid AND work_date = :d
                ORDER BY id DESC LIMIT 10
                """, Map.of("uid", userId, "d", date),
                "id", "workDate", "projectName", "description", "hours")
                : List.of());

        return out;
    }

    // ---------------------------------------------------------------------
    // What the data is trying to say
    // ---------------------------------------------------------------------

    /**
     * Attendance anomalies, computed rather than guessed. Each one is a question
     * somebody would otherwise have to think to ask: was the whole team late this
     * morning, did anybody forget to punch out, did several people punch from one
     * spot, has somebody stopped coming in without saying so.
     *
     * <p>Scoped to what the caller may see: their own if they are an employee,
     * their team if they lead one, everybody for HR and the admin.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> insights(Long requesterId, int days) {
        int window = Math.max(7, Math.min(180, days));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(window);

        User me = userRepository.findById(requesterId).orElseThrow(() -> ApiException.notFound("User"));
        boolean wide = canSeeOthers(requesterId);

        List<Long> scope;
        String scopeLabel;
        if (wide) {
            scope = userRepository.findAll().stream()
                    .filter(u -> u.isEnabled() && !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                    .map(User::getId).toList();
            scopeLabel = "everyone";
        } else {
            String title = me.getDesignationTitle() == null ? "" : me.getDesignationTitle().trim();
            List<User> team = title.isEmpty() ? List.of(me)
                    : userRepository.findTeammatesByTitleOrDesignation(title, me.getDesignationId());
            scope = team.isEmpty() ? List.of(requesterId) : team.stream().map(User::getId).toList();
            scopeLabel = title.isEmpty() ? "you" : "the " + title + " team";
        }

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("scope", scopeLabel);
        out.put("people", scope.size());
        out.put("windowDays", window);

        List<Map<String, Object>> findings = new java.util.ArrayList<>();

        // ---- this morning against the usual morning ----
        Map<String, Object> pace = one("""
                SELECT
                  AVG(CASE WHEN work_date = :today THEN late_minutes END)  AS today_late,
                  AVG(CASE WHEN work_date < :today THEN late_minutes END)  AS usual_late,
                  SUM(CASE WHEN work_date = :today AND is_late = 1 THEN 1 ELSE 0 END) AS late_today,
                  SUM(CASE WHEN work_date = :today AND punch_in_at IS NOT NULL THEN 1 ELSE 0 END) AS in_today
                FROM attendance
                WHERE work_date BETWEEN :from AND :today AND user_id IN (:ids)
                """, Map.of("today", today, "from", from, "ids", scope),
                "today_late", "usual_late", "late_today", "in_today");

        double todayLate = num(pace.get("today_late"));
        double usualLate = num(pace.get("usual_late"));
        long lateToday = (long) num(pace.get("late_today"));
        if (todayLate > 0 && usualLate > 0 && todayLate > usualLate * 1.6 && lateToday >= 3) {
            findings.add(finding("LATE_MORNING", "warn",
                    lateToday + " people were late this morning",
                    "Average " + Math.round(todayLate) + " minutes against the usual "
                            + Math.round(usualLate) + ". A whole team arriving late together is "
                            + "usually one cause, not several."));
        }

        // ---- punches with no punch-out ----
        List<Map<String, Object>> noOut = rows("""
                SELECT a.user_id, u.name, u.employee_code, COUNT(*) AS days
                FROM attendance a JOIN users u ON u.id = a.user_id
                WHERE a.work_date BETWEEN :from AND :yesterday
                  AND a.punch_in_at IS NOT NULL AND a.punch_out_at IS NULL
                  AND a.user_id IN (:ids)
                GROUP BY a.user_id, u.name, u.employee_code
                HAVING days >= 2 ORDER BY days DESC LIMIT 10
                """, Map.of("from", from, "yesterday", today.minusDays(1), "ids", scope),
                "user_id", "name", "employee_code", "days");
        for (Map<String, Object> r : noOut) {
            findings.add(personFinding("NO_PUNCH_OUT", "warn", r,
                    r.get("name") + " has not punched out on " + r.get("days") + " days",
                    "Hours worked cannot be counted for those days, and payroll reads them as short."));
        }

        // ---- several people punching from one spot ----
        List<Map<String, Object>> shared = rows("""
                SELECT a.work_date, ROUND(a.in_latitude, 4) AS lat, ROUND(a.in_longitude, 4) AS lng,
                       COUNT(DISTINCT a.user_id) AS people
                FROM attendance a
                WHERE a.work_date BETWEEN :from AND :today
                  AND a.in_latitude IS NOT NULL AND a.user_id IN (:ids)
                GROUP BY a.work_date, lat, lng
                HAVING people >= 3 ORDER BY people DESC LIMIT 5
                """, Map.of("from", from, "today", today, "ids", scope),
                "work_date", "lat", "lng", "people");
        for (Map<String, Object> r : shared) {
            findings.add(finding("SHARED_LOCATION", "info",
                    r.get("people") + " people punched from the same spot on " + r.get("work_date"),
                    "Normal at an office door or a site gate. Worth a look if it is not one of those."));
        }

        // ---- late as a habit rather than a bad day ----
        List<Map<String, Object>> habitual = rows("""
                SELECT a.user_id, u.name, u.employee_code,
                       SUM(a.is_late) AS late_days, COUNT(*) AS days,
                       ROUND(AVG(NULLIF(a.late_minutes,0))) AS avg_late
                FROM attendance a JOIN users u ON u.id = a.user_id
                WHERE a.work_date BETWEEN :from AND :today
                  AND a.punch_in_at IS NOT NULL AND a.user_id IN (:ids)
                GROUP BY a.user_id, u.name, u.employee_code
                HAVING days >= 5 AND late_days >= days * 0.6
                ORDER BY late_days DESC LIMIT 10
                """, Map.of("from", from, "today", today, "ids", scope),
                "user_id", "name", "employee_code", "late_days", "days", "avg_late");
        for (Map<String, Object> r : habitual) {
            findings.add(personFinding("HABITUAL_LATE", "info", r,
                    r.get("name") + " was late on " + r.get("late_days") + " of " + r.get("days") + " days",
                    "Averaging " + r.get("avg_late") + " minutes. A pattern rather than a bad morning."));
        }

        // ---- punches nobody's face was checked for ----
        Map<String, Object> unverified = one("""
                SELECT SUM(CASE WHEN face_verified = 0 THEN 1 ELSE 0 END) AS unverified,
                       COUNT(*) AS total
                FROM attendance
                WHERE work_date BETWEEN :from AND :today
                  AND punch_in_at IS NOT NULL AND user_id IN (:ids)
                """, Map.of("from", from, "today", today, "ids", scope),
                "unverified", "total");
        long unv = (long) num(unverified.get("unverified"));
        long tot = (long) num(unverified.get("total"));
        if (tot > 0 && unv > 0) {
            findings.add(finding("UNVERIFIED_PUNCHES", unv > tot / 2 ? "warn" : "info",
                    unv + " of " + tot + " punches had no face check",
                    unv == tot
                            ? "Nobody is using face verification yet. Enrolling faces is what makes a punch answerable for."
                            : "Those punches cannot be tied to a face afterwards."));
        }

        // ---- somebody who has quietly stopped coming in ----
        List<Map<String, Object>> gone = rows("""
                SELECT u.id AS user_id, u.name, u.employee_code, MAX(a.work_date) AS last_seen
                FROM users u LEFT JOIN attendance a
                       ON a.user_id = u.id AND a.punch_in_at IS NOT NULL
                WHERE u.id IN (:ids) AND u.enabled = 1
                GROUP BY u.id, u.name, u.employee_code
                HAVING last_seen IS NOT NULL AND last_seen < :cutoff
                ORDER BY last_seen ASC LIMIT 10
                """, Map.of("ids", scope, "cutoff", today.minusDays(4)),
                "user_id", "name", "employee_code", "last_seen");
        for (Map<String, Object> r : gone) {
            findings.add(personFinding("STOPPED_COMING", "alert", r,
                    r.get("name") + " last punched in on " + r.get("last_seen"),
                    "No punch since, and no leave covering it. Worth a call before it becomes a month."));
        }

        out.put("findings", findings);
        out.put("allClear", findings.isEmpty());
        return out;
    }

    // ---- small helpers for the two methods above ----

    /** True for HR, the admin and anyone holding the team-attendance permission. */
    private boolean canSeeOthers(Long userId) {
        Number n = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM user_roles ur
                JOIN role_permissions rp ON rp.role_id = ur.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE ur.user_id = :uid
                  AND p.code IN ('ATTENDANCE_TEAM','USER_MANAGE','EMPLOYEE_MANAGE','DASHBOARD_EXEC')
                """).setParameter("uid", userId).getSingleResult();
        return n != null && n.intValue() > 0;
    }

    private static Map<String, Object> finding(String code, String tone, String title, String detail) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("code", code);
        m.put("tone", tone);
        m.put("title", title);
        m.put("detail", detail);
        return m;
    }

    private static Map<String, Object> personFinding(String code, String tone,
                                                     Map<String, Object> row,
                                                     String title, String detail) {
        Map<String, Object> m = finding(code, tone, title, detail);
        m.put("userId", row.get("user_id"));
        m.put("employeeCode", row.get("employee_code"));
        return m;
    }

    /**
     * Whether a table really has these columns, checked before a statement naming
     * them is run.
     *
     * <p>This is not belt-and-braces. A native statement that fails marks the
     * whole transaction rollback-only, and it stays that way even when the
     * exception is caught â€” so one query against a column that does not exist
     * turns the entire request into a 500 rather than a missing panel. The only
     * safe order is to look first and never run a statement that cannot work.
     */
    private boolean hasColumns(String table, String... columns) {
        try {
            for (String col : columns) {
                Number n = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = :t AND column_name = :c
                        """).setParameter("t", table).setParameter("c", col).getSingleResult();
                if (n == null || n.intValue() == 0) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Runs a native query and labels each column, so the caller gets JSON keys. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String sql, Map<String, Object> params, String... keys) {
        var q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        List<Object[]> raw;
        try {
            raw = q.getResultList();
        } catch (Exception e) {
            // A finding is a courtesy. One that cannot be computed is skipped
            // rather than failing the whole panel.
            return List.of();
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object row : raw) {
            Object[] cells = row instanceof Object[] arr ? arr : new Object[]{row};
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i < keys.length && i < cells.length; i++) {
                m.put(keys[i], cells[i]);
            }
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> one(String sql, Map<String, Object> params, String... keys) {
        List<Map<String, Object>> r = rows(sql, params, keys);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0d;
    }
}
