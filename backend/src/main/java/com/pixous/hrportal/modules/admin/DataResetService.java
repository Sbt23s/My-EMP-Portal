package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clears the day-to-day records so the portal can be started fresh, without
 * touching anybody's employee record.
 *
 * <p>Two rules decide what is in scope and what is not. Anything <em>entered by
 * working the system</em> — a punch, a leave request, a payslip, a message — can
 * be cleared. Anything that <em>describes a person or how the company is set
 * up</em> is never touched: the employee record itself, logins and roles, teams
 * and departments, bank details, salary structures, leave types, holidays, the
 * asset inventory, chat rooms and their members, and every setting.
 *
 * <p>Nothing happens unless it is asked for area by area. There is no "clear
 * everything" — a caller has to name what they want gone, and confirm it, so a
 * stray click cannot empty the portal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataResetService {

    /** What each area clears, and what it deliberately leaves behind. */
    public enum Area {
        ATTENDANCE("Attendance punches", "Shifts, sites and holidays stay"),
        LEAVE("Leave requests, and the used count on balances",
                "Leave types, policies and the allocated days stay"),
        PERMISSION("Permission (short-leave) requests", ""),
        WORK_REPORTS("Work reports and their attachments", ""),
        TASKS("Tasks and every task discussion", ""),
        PAYROLL("Payslips, payroll runs, payslip requests, month-wise basic pay",
                "Salary structures and bank details stay"),
        CHAT("Chat messages, reactions, read marks and poll votes",
                "The rooms themselves and who is in them stay"),
        HELPDESK("Support tickets and their comments", ""),
        COMPLAINTS("Complaints and needs", ""),
        CLAIMS("Travel and expense claims", ""),
        NOTIFICATIONS("Notifications", ""),
        ASSET_ALLOCATIONS("Who is holding which asset", "The asset inventory itself stays"),
        PERFORMANCE("Performance reviews and goals", ""),
        SAFETY("Safety incidents", ""),
        ONBOARDING("Onboarding checklists and their tasks", ""),
        CALENDAR_EVENTS("Celebrations, meetings and training on the calendar",
                "Public holidays stay — those are not events"),
        LOGIN_HISTORY("Login history", "Nobody is signed out");

        public final String clears;
        public final String keeps;

        Area(String clears, String keeps) {
            this.clears = clears;
            this.keeps = keeps;
        }
    }

    private final com.pixous.hrportal.modules.attendance.AttendanceRepository attendanceRepository;
    private final com.pixous.hrportal.modules.leave.LeaveRequestRepository leaveRequestRepository;
    private final com.pixous.hrportal.modules.leave.LeaveBalanceRepository leaveBalanceRepository;
    private final com.pixous.hrportal.modules.leave.PermissionRequestRepository permissionRequestRepository;
    private final com.pixous.hrportal.modules.workreport.WorkReportRepository workReportRepository;
    private final com.pixous.hrportal.modules.task.TaskRepository taskRepository;
    private final com.pixous.hrportal.modules.task.TaskMessageRepository taskMessageRepository;
    private final com.pixous.hrportal.modules.payroll.PayslipRepository payslipRepository;
    private final com.pixous.hrportal.modules.payroll.PayrollRunRepository payrollRunRepository;
    private final com.pixous.hrportal.modules.payroll.PayslipRequestRepository payslipRequestRepository;
    private final com.pixous.hrportal.modules.payroll.SalaryMonthRepository salaryMonthRepository;
    private final com.pixous.hrportal.modules.community.CommunityMessageRepository communityMessageRepository;
    private final com.pixous.hrportal.modules.community.MessageReactionRepository messageReactionRepository;
    private final com.pixous.hrportal.modules.community.MessageReadRepository messageReadRepository;
    private final com.pixous.hrportal.modules.community.PollVoteRepository pollVoteRepository;
    private final com.pixous.hrportal.modules.helpdesk.TicketRepository ticketRepository;
    private final com.pixous.hrportal.modules.helpdesk.TicketCommentRepository ticketCommentRepository;
    private final com.pixous.hrportal.modules.complaint.ComplaintNeedRepository complaintNeedRepository;
    private final com.pixous.hrportal.modules.expense.TaExpenseRepository taExpenseRepository;
    private final com.pixous.hrportal.modules.notification.NotificationRepository notificationRepository;
    private final com.pixous.hrportal.modules.asset.AssetAllocationRepository assetAllocationRepository;
    private final com.pixous.hrportal.modules.performance.PerformanceReviewRepository performanceReviewRepository;
    private final com.pixous.hrportal.modules.performance.PerformanceGoalRepository performanceGoalRepository;
    private final com.pixous.hrportal.modules.safety.SafetyIncidentRepository safetyIncidentRepository;
    private final com.pixous.hrportal.modules.onboarding.OnboardingChecklistRepository onboardingChecklistRepository;
    private final com.pixous.hrportal.modules.onboarding.OnboardingTaskRepository onboardingTaskRepository;
    private final com.pixous.hrportal.modules.calendar.CompanyEventRepository companyEventRepository;
    private final com.pixous.hrportal.modules.auth.LoginHistoryRepository loginHistoryRepository;

    /** How much each area is currently holding, so nothing is cleared blind. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> preview() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Area area : Area.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("area", area.name());
            row.put("clears", area.clears);
            row.put("keeps", area.keeps);
            row.put("count", count(area));
            out.add(row);
        }
        return out;
    }

    /**
     * Clears the named areas. The confirmation is checked here rather than only
     * in the browser, so nothing can be emptied by a stray request.
     */
    @Transactional
    public Map<String, Object> reset(Set<String> areaNames, String confirmation, Long actorId) {
        if (!"RESET".equals(confirmation)) {
            throw ApiException.business("Type RESET to confirm.");
        }
        if (areaNames == null || areaNames.isEmpty()) {
            throw ApiException.business("Choose at least one thing to clear.");
        }

        Set<Area> areas = new LinkedHashSet<>();
        for (String name : areaNames) {
            try {
                areas.add(Area.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw ApiException.business("There is nothing called " + name + ".");
            }
        }

        Map<String, Object> cleared = new LinkedHashMap<>();
        for (Area area : areas) {
            long before = count(area);
            clear(area);
            cleared.put(area.name(), before);
            log.warn("Data reset: {} cleared ({} rows) by user {}", area, before, actorId);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cleared", cleared);
        out.put("total", cleared.values().stream().mapToLong(v -> (Long) v).sum());
        return out;
    }

    // ---- how much is there ----

    private long count(Area area) {
        return switch (area) {
            case ATTENDANCE -> attendanceRepository.count();
            case LEAVE -> leaveRequestRepository.count();
            case PERMISSION -> permissionRequestRepository.count();
            case WORK_REPORTS -> workReportRepository.count();
            case TASKS -> taskRepository.count();
            case PAYROLL -> payslipRepository.count() + payslipRequestRepository.count()
                    + salaryMonthRepository.count();
            case CHAT -> communityMessageRepository.count();
            case HELPDESK -> ticketRepository.count();
            case COMPLAINTS -> complaintNeedRepository.count();
            case CLAIMS -> taExpenseRepository.count();
            case NOTIFICATIONS -> notificationRepository.count();
            case ASSET_ALLOCATIONS -> assetAllocationRepository.count();
            case PERFORMANCE -> performanceReviewRepository.count() + performanceGoalRepository.count();
            case SAFETY -> safetyIncidentRepository.count();
            case ONBOARDING -> onboardingChecklistRepository.count();
            case CALENDAR_EVENTS -> companyEventRepository.count();
            case LOGIN_HISTORY -> loginHistoryRepository.count();
        };
    }

    // ---- clearing, child rows before parents ----

    private void clear(Area area) {
        switch (area) {
            case ATTENDANCE -> attendanceRepository.deleteAllInBatch();

            case LEAVE -> {
                leaveRequestRepository.deleteAllInBatch();
                // The allocation is HR's decision and stays; only what has been
                // spent against it goes back to zero, or every balance would read
                // as used up with no request left to explain it.
                leaveBalanceRepository.findAll().forEach(b -> b.setUsed(BigDecimal.ZERO));
            }

            case PERMISSION -> permissionRequestRepository.deleteAllInBatch();
            case WORK_REPORTS -> workReportRepository.deleteAllInBatch();

            case TASKS -> {
                taskMessageRepository.deleteAllInBatch();
                taskRepository.deleteAllInBatch();
            }

            case PAYROLL -> {
                payslipRequestRepository.deleteAllInBatch();
                payslipRepository.deleteAllInBatch();
                payrollRunRepository.deleteAllInBatch();
                salaryMonthRepository.deleteAllInBatch();
            }

            case CHAT -> {
                messageReactionRepository.deleteAllInBatch();
                messageReadRepository.deleteAllInBatch();
                pollVoteRepository.deleteAllInBatch();
                // A reply points at its parent, so the children have to go first.
                communityMessageRepository.findAll().stream()
                        .filter(m -> m.getParentId() != null)
                        .forEach(communityMessageRepository::delete);
                communityMessageRepository.deleteAll();
            }

            case HELPDESK -> {
                ticketCommentRepository.deleteAllInBatch();
                ticketRepository.deleteAllInBatch();
            }

            case COMPLAINTS -> complaintNeedRepository.deleteAllInBatch();

            case CLAIMS -> taExpenseRepository.deleteAllInBatch();

            case NOTIFICATIONS -> notificationRepository.deleteAllInBatch();
            case ASSET_ALLOCATIONS -> assetAllocationRepository.deleteAllInBatch();

            case PERFORMANCE -> {
                performanceReviewRepository.deleteAllInBatch();
                performanceGoalRepository.deleteAllInBatch();
            }

            case SAFETY -> safetyIncidentRepository.deleteAllInBatch();

            case ONBOARDING -> {
                onboardingTaskRepository.deleteAllInBatch();
                onboardingChecklistRepository.deleteAllInBatch();
            }

            case CALENDAR_EVENTS -> companyEventRepository.deleteAllInBatch();
            case LOGIN_HISTORY -> loginHistoryRepository.deleteAllInBatch();
        }
    }
}
