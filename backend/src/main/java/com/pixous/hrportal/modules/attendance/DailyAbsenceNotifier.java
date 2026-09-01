package com.pixous.hrportal.modules.attendance;

import com.pixous.hrportal.common.WorkCalendar;
import com.pixous.hrportal.modules.org.Holiday;
import com.pixous.hrportal.modules.org.HolidayRepository;
import com.pixous.hrportal.modules.leave.LeaveRequest;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who is on leave and who simply did not turn up, told to HR and the CTO each
 * morning.
 *
 * <p>Both facts already exist -- the dashboard has an "Absent today" widget and
 * the leave module knows who is approved -- but they had to be gone looking
 * for. The people who act on an unexplained absence are the ones who should not
 * have to check.
 *
 * <p>The two lists are deliberately kept apart. Somebody on approved leave is
 * accounted for and needs no chasing; somebody absent with no leave against
 * their name is the one worth a phone call. Collapsing them into a single
 * "not in today" count would hide exactly the distinction that makes the
 * message worth sending.
 *
 * <p>Sent per company. This runs on a timer, so there is no signed-in user and
 * the tenant filter is inactive -- every query here sees the whole platform,
 * which is what lets one pass cover every company and is also how a digest
 * could otherwise name another company's staff. Each company's HR and CTO are
 * told about their own people and nobody else's.
 *
 * <p>Nothing here throws. A digest is a courtesy: a failure to build it must
 * not take the scheduler down or affect attendance itself, so it is logged and
 * swallowed, as the other notifiers do.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyAbsenceNotifier {

    /** The company head, who receives a copy. Matches {@code OversightNotifier}. */
    private static final String CTO_CODE = "PIX-E100";

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final NotificationService notificationService;

    /**
     * 10:00, after the morning's punch-ins have landed.
     *
     * <p>Late enough that somebody who arrived on time is not reported missing,
     * which would make the digest something people learn to distrust.
     */
    @Scheduled(cron = "0 0 10 * * *")
    public void notifyTodaysAttendance() {
        try {
            LocalDate today = LocalDate.now();
            if (!isWorkingDay(today)) return;
            sendFor(today);
        } catch (Exception e) {
            log.error("Daily absence digest failed", e);
        }
    }

    /**
     * Builds and sends the digest for one day.
     *
     * <p>Separate from the schedule so it can be tested, and called again
     * without waiting for tomorrow.
     */
    public void sendFor(LocalDate day) {
        List<User> staff = userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                .toList();
        if (staff.isEmpty()) return;

        Set<Long> punchedIn = attendanceRepository.findByWorkDate(day).stream()
                .filter(a -> a.getPunchInAt() != null)
                .map(Attendance::getUserId)
                .collect(Collectors.toSet());
        Set<Long> onLeave = leaveRequestRepository.findOnLeave(day).stream()
                .map(LeaveRequest::getUserId)
                .collect(Collectors.toSet());

        // One digest per company, so nobody reads another tenant's roll call.
        Map<Long, List<User>> byCompany = staff.stream()
                .filter(u -> u.getCompanyId() != null)
                .collect(Collectors.groupingBy(User::getCompanyId));

        byCompany.forEach((companyId, people) -> {
            List<String> leaveNames = new ArrayList<>();
            List<String> absentNames = new ArrayList<>();
            for (User u : people) {
                if (onLeave.contains(u.getId())) {
                    leaveNames.add(label(u));
                } else if (!punchedIn.contains(u.getId())) {
                    absentNames.add(label(u));
                }
            }
            // Nothing to report is not worth a notification. A bell that rings
            // on a full-attendance day is a bell people stop opening.
            if (leaveNames.isEmpty() && absentNames.isEmpty()) return;

            String title = "Today: " + absentNames.size() + " absent, "
                    + leaveNames.size() + " on leave";
            StringBuilder body = new StringBuilder();
            if (!absentNames.isEmpty()) {
                body.append("Absent (no leave applied): ").append(join(absentNames));
            }
            if (!leaveNames.isEmpty()) {
                if (body.length() > 0) body.append(" · ");
                body.append("On approved leave: ").append(join(leaveNames));
            }

            recipients(companyId).forEach(id -> notificationService.createAndPush(
                    id, title, body.toString(), "ATTENDANCE_DIGEST", "/attendance"));
        });
    }

    /**
     * HR, the administrators and the CTO of one company.
     *
     * <p>Resolved the same way {@code OversightNotifier} does -- by permission
     * for HR and the administrators, by employee code for the CTO -- then
     * narrowed to the company being reported on, because these lookups are
     * unfiltered inside a scheduled job.
     */
    private Set<Long> recipients(Long companyId) {
        Set<Long> ids = new LinkedHashSet<>();
        userRepository.findByPermission("USER_MANAGE").stream()
                .filter(User::isEnabled)
                .filter(u -> companyId.equals(u.getCompanyId()))
                .forEach(u -> ids.add(u.getId()));
        userRepository.findByPermission("COMPLAINT_MANAGE").stream()
                .filter(User::isEnabled)
                .filter(u -> companyId.equals(u.getCompanyId()))
                .forEach(u -> ids.add(u.getId()));
        userRepository.findByEmployeeCode(CTO_CODE)
                .filter(User::isEnabled)
                .filter(u -> companyId.equals(u.getCompanyId()))
                .ifPresent(u -> ids.add(u.getId()));
        return ids;
    }

    /** A name the reader recognises, with the employee code to disambiguate. */
    private static String label(User u) {
        String code = u.getEmployeeCode();
        return code == null || code.isBlank() ? u.getName() : u.getName() + " (" + code + ")";
    }

    /**
     * The first few names, then a count.
     *
     * <p>A notification body that runs to thirty names is one nobody finishes.
     * The full list is on the attendance page the notification links to.
     */
    private static String join(List<String> names) {
        int shown = Math.min(names.size(), 6);
        String head = String.join(", ", names.subList(0, shown));
        int rest = names.size() - shown;
        return rest > 0 ? head + " and " + rest + " more" : head;
    }

    private boolean isWorkingDay(LocalDate day) {
        if (WorkCalendar.isWeekend(day)) return false;
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(day, day).stream()
                .map(Holiday::getHolidayDate)
                .noneMatch(day::equals);
    }
}
