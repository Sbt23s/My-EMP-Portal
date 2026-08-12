package com.pixous.hrportal.modules.attendance;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.attendance.dto.AttendanceResponse;
import com.pixous.hrportal.modules.attendance.dto.AttendanceSummary;
import com.pixous.hrportal.modules.attendance.dto.PunchRequest;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance", description = "GPS punch in/out, calendar, monthly summary, team view")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    public AttendanceController(AttendanceService attendanceService,
                                UserRepository userRepository) {
        this.attendanceService = attendanceService;
        this.userRepository = userRepository;
    }

    @PostMapping("/punch-in")
    @Operation(summary = "Punch in (GPS coordinates optional)")
    public ApiResponse<AttendanceResponse> punchIn(@RequestBody(required = false) PunchRequest request) {
        return ApiResponse.ok(attendanceService.punchIn(SecurityUtils.currentUserId(), orEmpty(request)),
                "Punched in");
    }

    @PostMapping("/punch-out")
    @Operation(summary = "Punch out (GPS coordinates optional)")
    public ApiResponse<AttendanceResponse> punchOut(@RequestBody(required = false) PunchRequest request) {
        return ApiResponse.ok(attendanceService.punchOut(SecurityUtils.currentUserId(), orEmpty(request)),
                "Punched out");
    }

    /** A missing request body (no GPS sent) becomes an empty punch instead of a 400 error. */
    private static PunchRequest orEmpty(PunchRequest r) {
        return r != null ? r : new PunchRequest(null, null, null, null, null, null);
    }

    /**
     * Punch in or out with a verified face.
     *
     * <p>Verification itself happens in the analytics service, which holds the
     * enrolments; what arrives here is its verdict, the selfie it was taken from
     * and how close the match was. The photo is stored so that a punch can be
     * answered for months later — which is most of what verification is for, and
     * was the piece missing when the face service existed but nothing recorded
     * having used it.
     *
     * <p>A punch is not refused for failing verification: refusing it would mean
     * somebody whose camera or enrolment lets them down cannot mark attendance at
     * all. It is recorded as unverified, and HR can see exactly which ones those
     * were.
     */
    @PostMapping(path = "/face-punch", consumes = "multipart/form-data")
    @Operation(summary = "Punch in/out with a face-verified selfie")
    public ApiResponse<AttendanceResponse> facePunch(
            @RequestParam("kind") String kind,
            @RequestParam(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo,
            @RequestParam(value = "verified", defaultValue = "false") boolean verified,
            @RequestParam(value = "score", required = false) java.math.BigDecimal score,
            @RequestParam(value = "detail", required = false) String detail,
            @RequestParam(value = "latitude", required = false) java.math.BigDecimal latitude,
            @RequestParam(value = "longitude", required = false) java.math.BigDecimal longitude,
            @RequestParam(value = "accuracy", required = false) Integer accuracy,
            @RequestParam(value = "mode", required = false) String mode,
            jakarta.servlet.http.HttpServletRequest http) {

        Long userId = SecurityUtils.currentUserId();
        boolean punchIn = !"punch-out".equalsIgnoreCase(kind);
        PunchRequest req = new PunchRequest(latitude, longitude,
                mode == null || mode.isBlank() ? "FACE_VERIFIED" : mode,
                null, null, null);

        AttendanceResponse response = attendanceService.facePunch(
                userId, punchIn, req, verified, score, detail, photo, accuracy,
                http.getHeader("User-Agent"));
        return ApiResponse.ok(response, punchIn ? "Punched in" : "Punched out");
    }

    /**
     * What somebody was actually doing on a given day: the punch, where it was
     * made from, and the work recorded against it. Attendance on its own says
     * somebody was present; this says what being present amounted to.
     */
    @GetMapping("/day")
    @Operation(summary = "One person's punch, location and work for one day")
    public ApiResponse<java.util.Map<String, Object>> dayDetail(
            @RequestParam(required = false) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long target = userId == null ? SecurityUtils.currentUserId() : userId;
        return ApiResponse.ok(attendanceService.dayDetail(target, date, SecurityUtils.currentUserId()));
    }

    /**
     * What the attendance data is trying to tell somebody, computed rather than
     * guessed: a late morning across a whole team, punches that never got a
     * punch-out, several people punching from one spot, a run of unexplained
     * absence. Scoped to what the caller is allowed to see.
     */
    @GetMapping("/insights")
    @Operation(summary = "Attendance anomalies and patterns for the caller's scope")
    public ApiResponse<java.util.Map<String, Object>> insights(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(attendanceService.insights(SecurityUtils.currentUserId(), days));
    }

    @GetMapping("/today")
    @Operation(summary = "Today's attendance record for the signed-in user")
    public ApiResponse<AttendanceResponse> today() {
        return ApiResponse.ok(attendanceService.today(SecurityUtils.currentUserId()));
    }

    @GetMapping("/me")
    @Operation(summary = "Attendance calendar between two dates")
    public ApiResponse<List<AttendanceResponse>> myCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(attendanceService.myCalendar(SecurityUtils.currentUserId(), from, to));
    }

    @GetMapping("/me/summary")
    @Operation(summary = "Monthly attendance summary (present/wfh/late/absent/overtime)")
    public ApiResponse<AttendanceSummary> summary(
            @RequestParam int month, @RequestParam int year) {
        return ApiResponse.ok(attendanceService.summary(SecurityUtils.currentUserId(), month, year));
    }

    @GetMapping("/absent-today")
    @Operation(summary = "Everyone absent today (no punch, not on leave) — visible to every employee")
    public ApiResponse<List<com.pixous.hrportal.modules.attendance.dto.TodayStatusEntry>> absentToday() {
        return ApiResponse.ok(attendanceService.todayAbsentees());
    }

    @GetMapping("/my-team-today")
    @Operation(summary = "Punch-in status today for the caller's own team")
    public ApiResponse<List<com.pixous.hrportal.modules.attendance.dto.TeamPresenceEntry>> myTeamToday() {
        return ApiResponse.ok(attendanceService.myTeamPresenceToday(SecurityUtils.currentUserId()));
    }

    @GetMapping("/team")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_TEAM','USER_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "Team attendance for a given date (direct reports)")
    public ApiResponse<List<AttendanceResponse>> team(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<Long> memberIds = teamMemberIds();
        return ApiResponse.ok(attendanceService.teamForDate(memberIds, target));
    }

    @GetMapping("/team-range")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_TEAM','USER_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "Team attendance across a date range, for reports")
    public ApiResponse<List<AttendanceResponse>> teamRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw com.pixous.hrportal.common.ApiException.business("The end date is before the start date");
        }
        if (from.plusDays(370).isBefore(to)) {
            throw com.pixous.hrportal.common.ApiException.business("Please choose a range of a year or less");
        }
        return ApiResponse.ok(attendanceService.teamForRange(teamMemberIds(), from, to));
    }


    /**
     * Whose attendance the caller may read: admins, HR, executives and managers
     * see everyone; a Team Leader sees only their own team; anyone else sees
     * their direct reports.
     */
    private List<Long> teamMemberIds() {
        Long managerId = SecurityUtils.currentUserId();
        User me = userRepository.findById(managerId).orElse(null);
        boolean isManagerRole = hasRole(me, "IT_MGR");
        boolean isTeamLeader = hasRole(me, "IT_TL");

        List<Long> memberIds;
        if (SecurityUtils.hasAuthority("USER_MANAGE") || SecurityUtils.hasAuthority("DASHBOARD_EXEC")
                || isManagerRole) {
            // Admin, HR, executives and managers see every active/onboarding employee.
            memberIds = userRepository.findAll().stream()
                    .filter(AttendanceController::isCurrentEmployee)
                    .map(User::getId).toList();
        } else if (isTeamLeader) {
            // Team Leaders see only their own team (same designation title).
            String myTitle = me == null || me.getDesignationTitle() == null ? "" : me.getDesignationTitle().trim();
            memberIds = userRepository.findAll().stream()
                    .filter(AttendanceController::isCurrentEmployee)
                    .filter(u -> u.getDesignationTitle() != null
                            && u.getDesignationTitle().trim().equalsIgnoreCase(myTitle))
                    .map(User::getId).toList();
        } else if (SecurityUtils.hasAuthority("ATTENDANCE_TEAM")) {
            memberIds = userRepository.findAll().stream()
                    .filter(AttendanceController::isCurrentEmployee)
                    .map(User::getId).toList();
        } else {
            memberIds = userRepository.findByReportingManagerId(managerId).stream()
                    .filter(AttendanceController::isCurrentEmployee)
                    .map(User::getId).toList();
        }
        return memberIds;
    }

    private static boolean hasRole(User u, String code) {
        return u != null && u.getRoles().stream().anyMatch(r -> code.equals(r.getCode()));
    }

    /** Active or onboarding employees only — offboarded/disabled are excluded. */
    private static boolean isCurrentEmployee(User u) {
        return u != null && u.isEnabled()
                && !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus());
    }
}
