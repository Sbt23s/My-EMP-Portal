package com.pixous.hrportal.modules.workreport;

import com.pixous.hrportal.common.SmsService;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.org.Holiday;
import com.pixous.hrportal.modules.org.HolidayRepository;
import com.pixous.hrportal.modules.org.SystemSetting;
import com.pixous.hrportal.modules.org.SystemSettingRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The daily nudge for a work report that has not been filed.
 *
 * <p>Deliberately quiet about who it does not chase: a Sunday, a company
 * holiday, and anybody on approved leave are all skipped, because a reminder
 * that arrives on a day off teaches people to ignore reminders. So does one
 * that arrives twice, which is why the day it last ran is written down — a
 * restart in the evening cannot repeat it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportReminderService {

    private static final String ENABLED = "workreport.reminder_enabled";
    private static final String TIME = "workreport.reminder_time";
    private static final String LAST_RUN = "workreport.reminder_last_run";
    private static final LocalTime DEFAULT_TIME = LocalTime.of(18, 30);

    private final WorkReportRepository reportRepository;
    private final UserRepository userRepository;
    private final SystemSettingRepository settingRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final NotificationService notificationService;
    private final SmsService smsService;

    // ---- the setting, as HR sees and sets it ----

    @Transactional(readOnly = true)
    public Map<String, Object> settings() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled());
        out.put("time", reminderTime().toString());
        String last = value(LAST_RUN);
        out.put("lastRun", last == null || last.isBlank() ? null : last);
        return out;
    }

    @Transactional
    public void saveSettings(boolean on, String time) {
        LocalTime parsed;
        try {
            parsed = LocalTime.parse(time);
        } catch (Exception e) {
            throw com.pixous.hrportal.common.ApiException.business(
                    "Give the time as HH:mm, for example 18:30.");
        }
        put(ENABLED, Boolean.toString(on));
        put(TIME, parsed.toString());
    }

    /** Who has not filed a report for the given day, and who was let off. */
    @Transactional(readOnly = true)
    public Map<String, Object> pendingToday(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", day.toString());
        out.put("workingDay", isWorkingDay(day));
        List<User> missing = whoHasNotFiled(day);
        out.put("pendingCount", missing.size());
        out.put("pending", missing.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getId());
            m.put("name", u.getName());
            m.put("employeeCode", u.getEmployeeCode());
            m.put("team", u.getDesignationTitle());
            return m;
        }).toList());
        return out;
    }

    /**
     * Sends the reminder now, whatever the clock says. This is the button HR
     * presses when they want to chase people early; it returns how many were
     * chased so the page can say so.
     */
    @Transactional
    public int remindNow(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<User> missing = whoHasNotFiled(day);
        missing.forEach(u -> nudge(u, day));
        return missing.size();
    }

    // ---- the clock ----

    /**
     * Checked every five minutes rather than scheduled at the configured time,
     * because that time is a setting somebody can change while the application
     * is running and a cron expression is fixed when it starts.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void tick() {
        try {
            if (!enabled()) return;
            LocalDate today = LocalDate.now();
            if (today.toString().equals(value(LAST_RUN))) return;

            LocalTime due = reminderTime();
            if (LocalTime.now().isBefore(due)) return;

            // Mark the day done before sending: a failure halfway through must
            // not cause the whole list to be chased again five minutes later.
            put(LAST_RUN, today.toString());

            if (!isWorkingDay(today)) {
                log.info("Work report reminder skipped — {} is not a working day", today);
                return;
            }
            List<User> missing = whoHasNotFiled(today);
            missing.forEach(u -> nudge(u, today));
            log.info("Work report reminder sent to {} employee(s) for {}", missing.size(), today);
        } catch (Exception e) {
            log.error("Work report reminder failed", e);
        }
    }

    // ---- who, and how ----

    /**
     * Everybody expected to file for this day who has not. Only enabled accounts
     * count, and anybody who joined after the day is not expected to have worked
     * it.
     */
    private List<User> whoHasNotFiled(LocalDate day) {
        Set<Long> filed = reportRepository.findByWorkDate(day).stream()
                .map(WorkReport::getUserId)
                .collect(Collectors.toSet());

        Set<Long> onLeave = leaveRequestRepository.findOnLeave(day).stream()
                .map(r -> r.getUserId())
                .collect(Collectors.toSet());

        return userRepository.findByEnabledTrue().stream()
                .filter(u -> !filed.contains(u.getId()))
                .filter(u -> !onLeave.contains(u.getId()))
                .filter(u -> u.getDateOfJoining() == null || !u.getDateOfJoining().isAfter(day))
                .filter(u -> !"OFFBOARDED".equalsIgnoreCase(String.valueOf(u.getProfileStatus())))
                .toList();
    }

    private void nudge(User user, LocalDate day) {
        try {
            notificationService.createAndPush(
                    user.getId(),
                    "Work report pending",
                    "Your work report for " + day + " has not been submitted yet.",
                    "WORK_REPORT",
                    "/work-reports");
            smsService.send(user.getPhone(),
                    "Pixous HR: Hi " + user.getName()
                            + ", your work report for " + day + " is not submitted yet."
                            + " Please fill it in the portal.");
        } catch (Exception e) {
            log.debug("Could not remind {}", user.getId(), e);
        }
    }

    /** Sundays and company holidays are not chased. */
    private boolean isWorkingDay(LocalDate day) {
        if (day.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) return false;
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(day, day).stream()
                .map(Holiday::getHolidayDate)
                .noneMatch(day::equals);
    }

    // ---- settings plumbing ----

    private boolean enabled() {
        String raw = value(ENABLED);
        return raw == null || raw.isBlank() || Boolean.parseBoolean(raw);
    }

    private LocalTime reminderTime() {
        String raw = value(TIME);
        if (raw == null || raw.isBlank()) return DEFAULT_TIME;
        try {
            return LocalTime.parse(raw.trim());
        } catch (Exception e) {
            return DEFAULT_TIME;
        }
    }

    private String value(String key) {
        return settingRepository.findById(key).map(SystemSetting::getValue).orElse(null);
    }

    private void put(String key, String value) {
        SystemSetting setting = settingRepository.findById(key).orElseGet(() -> {
            SystemSetting fresh = new SystemSetting();
            fresh.setKey(key);
            return fresh;
        });
        setting.setValue(value);
        settingRepository.save(setting);
    }
}
