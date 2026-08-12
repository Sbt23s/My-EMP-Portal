package com.pixous.hrportal.modules.task;

import com.pixous.hrportal.common.SmsService;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.org.SystemSetting;
import com.pixous.hrportal.modules.org.SystemSettingRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How much work each person is carrying, and the nudges as a due date arrives.
 *
 * <p>The workload count exists so that whoever is about to assign something can
 * see who is already buried. It counts what is open, and separates what is
 * already late from what is merely coming, because those need different answers.
 *
 * <p>Reminders go out three times at most — once before the due date, once on
 * it, and once a day while it stays late — and each is written down against the
 * task on the day it is sent, so a restart cannot repeat one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskWorkloadService {

    private static final String ENABLED = "task.reminder_enabled";
    private static final String TIME = "task.reminder_time";
    private static final String LEAD_DAYS = "task.reminder_lead_days";
    private static final LocalTime DEFAULT_TIME = LocalTime.of(9, 30);
    private static final int DEFAULT_LEAD_DAYS = 1;
    private static final String COMPANY_HEAD_CODE = "PIX-E100";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SystemSettingRepository settingRepository;
    private final NotificationService notificationService;
    private final SmsService smsService;

    // ---- workload ----

    /**
     * One row per person carrying open work, heaviest first.
     *
     * <p>Scope follows what the caller is already allowed to see: the admin, HR
     * and the company head get everybody; a Team Leader gets their own team,
     * which is exactly the set they can assign to.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> workload(Long requesterId) {
        List<Task> open = taskRepository.findByStatusNot("COMPLETED");
        if (open.isEmpty()) return List.of();

        Map<Long, User> people = userRepository.findAllById(
                        open.stream().map(Task::getAssignedTo).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        java.util.Set<Long> visible = visibleAssignees(requesterId, people.values());
        LocalDate today = LocalDate.now();

        Map<Long, int[]> tally = new LinkedHashMap<>();
        for (Task t : open) {
            if (t.getAssignedTo() == null || !visible.contains(t.getAssignedTo())) continue;
            int[] counts = tally.computeIfAbsent(t.getAssignedTo(), k -> new int[3]);
            counts[0]++;                                             // active
            if (t.getDueDate() != null) {
                if (t.getDueDate().isBefore(today)) counts[1]++;      // overdue
                else if (!t.getDueDate().isAfter(today.plusDays(2))) counts[2]++;  // due soon
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        tally.forEach((userId, counts) -> {
            User u = people.get(userId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("name", u != null ? u.getName() : "Unknown");
            row.put("employeeCode", u != null ? u.getEmployeeCode() : null);
            row.put("team", u != null ? u.getDesignationTitle() : null);
            row.put("activeCount", counts[0]);
            row.put("overdueCount", counts[1]);
            row.put("dueSoonCount", counts[2]);
            out.add(row);
        });
        out.sort((a, b) -> ((Integer) b.get("activeCount")) - ((Integer) a.get("activeCount")));
        return out;
    }

    /** Whose workload this caller is entitled to see. */
    private java.util.Set<Long> visibleAssignees(Long requesterId, java.util.Collection<User> candidates) {
        java.util.Set<Long> all = candidates.stream().map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (SecurityUtils.hasAuthority("USER_MANAGE")) return all;
        if (SecurityUtils.hasAuthority("TASK_VIEW_ALL")) return all;

        User me = userRepository.findById(requesterId).orElse(null);
        if (me == null) return java.util.Set.of();
        if (COMPANY_HEAD_CODE.equalsIgnoreCase(String.valueOf(me.getEmployeeCode()))) return all;

        String mine = me.getDesignationTitle();
        if (mine == null || mine.isBlank()) return java.util.Set.of(requesterId);
        String needle = mine.trim();
        return candidates.stream()
                .filter(u -> u.getDesignationTitle() != null
                        && needle.equalsIgnoreCase(u.getDesignationTitle().trim()))
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ---- reminder settings ----

    @Transactional(readOnly = true)
    public Map<String, Object> settings() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled());
        out.put("time", reminderTime().toString());
        out.put("leadDays", leadDays());
        return out;
    }

    @Transactional
    public void saveSettings(boolean on, String time, Integer leadDays) {
        LocalTime parsed;
        try {
            parsed = LocalTime.parse(time);
        } catch (Exception e) {
            throw com.pixous.hrportal.common.ApiException.business(
                    "Give the time as HH:mm, for example 09:30.");
        }
        int lead = leadDays == null ? DEFAULT_LEAD_DAYS : leadDays;
        if (lead < 0 || lead > 30) {
            throw com.pixous.hrportal.common.ApiException.business(
                    "Remind between 0 and 30 days before the due date.");
        }
        put(ENABLED, Boolean.toString(on));
        put(TIME, parsed.toString());
        put(LEAD_DAYS, String.valueOf(lead));
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
            if (LocalTime.now().isBefore(reminderTime())) return;
            runReminders();
        } catch (Exception e) {
            log.error("Task reminders failed", e);
        }
    }

    /**
     * Sends whichever reminders are due today. Safe to call twice: the day is
     * written against each task as it is sent, so the second call does nothing.
     */
    @Transactional
    public int runReminders() {
        LocalDate today = LocalDate.now();
        int lead = leadDays();
        int sent = 0;

        for (Task t : taskRepository.findByStatusNot("COMPLETED")) {
            if (t.getDueDate() == null || t.getAssignedTo() == null) continue;
            User assignee = userRepository.findById(t.getAssignedTo()).orElse(null);
            if (assignee == null) continue;

            LocalDate due = t.getDueDate();
            if (due.isBefore(today)) {
                if (today.equals(t.getRemindedOverdue())) continue;
                long late = java.time.temporal.ChronoUnit.DAYS.between(due, today);
                nudge(t, assignee,
                        "Task overdue",
                        "\"" + t.getTitle() + "\" was due on " + due
                                + " — " + late + (late == 1 ? " day" : " days") + " late.");
                t.setRemindedOverdue(today);
                taskRepository.save(t);
                alsoTellAssigner(t, assignee);
                sent++;
            } else if (due.equals(today)) {
                if (today.equals(t.getRemindedDue())) continue;
                nudge(t, assignee, "Task due today",
                        "\"" + t.getTitle() + "\" is due today.");
                t.setRemindedDue(today);
                taskRepository.save(t);
                sent++;
            } else if (!due.isAfter(today.plusDays(lead))) {
                if (today.equals(t.getRemindedBefore())) continue;
                long days = java.time.temporal.ChronoUnit.DAYS.between(today, due);
                nudge(t, assignee, "Task due soon",
                        "\"" + t.getTitle() + "\" is due in "
                                + days + (days == 1 ? " day" : " days") + " (" + due + ").");
                t.setRemindedBefore(today);
                taskRepository.save(t);
                sent++;
            }
        }
        if (sent > 0) log.info("Task reminders sent: {}", sent);
        return sent;
    }

    private void nudge(Task task, User assignee, String title, String body) {
        try {
            notificationService.createAndPush(assignee.getId(), title, body, "TASK",
                    "/tasks?chat=" + task.getId());
            smsService.send(assignee.getPhone(),
                    "Pixous HR: Hi " + assignee.getName() + ", " + body);
        } catch (Exception e) {
            log.debug("Could not remind {} about task {}", assignee.getId(), task.getId(), e);
        }
    }

    /** An overdue task is the assigner's problem too, so they hear about it once. */
    private void alsoTellAssigner(Task task, User assignee) {
        if (task.getAssignedBy() == null || task.getAssignedBy().equals(assignee.getId())) return;
        try {
            notificationService.createAndPush(
                    task.getAssignedBy(),
                    "Task overdue",
                    assignee.getName() + " has not finished \"" + task.getTitle()
                            + "\", due " + task.getDueDate() + ".",
                    "TASK",
                    "/tasks?chat=" + task.getId());
        } catch (Exception e) {
            log.debug("Could not tell the assigner about task {}", task.getId(), e);
        }
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

    private int leadDays() {
        String raw = value(LEAD_DAYS);
        if (raw == null || raw.isBlank()) return DEFAULT_LEAD_DAYS;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed < 0 || parsed > 30 ? DEFAULT_LEAD_DAYS : parsed;
        } catch (Exception e) {
            return DEFAULT_LEAD_DAYS;
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
