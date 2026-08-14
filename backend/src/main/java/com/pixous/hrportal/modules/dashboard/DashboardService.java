package com.pixous.hrportal.modules.dashboard;

import com.pixous.hrportal.modules.asset.AssetRepository;
import com.pixous.hrportal.modules.attendance.Attendance;
import com.pixous.hrportal.modules.attendance.AttendanceRepository;
import com.pixous.hrportal.modules.dashboard.dto.EmployeeDashboard;
import com.pixous.hrportal.modules.dashboard.dto.ExecutiveDashboard;
import com.pixous.hrportal.modules.helpdesk.TicketRepository;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.leave.LeaveService;
import com.pixous.hrportal.modules.notification.Notification;
import com.pixous.hrportal.modules.notification.NotificationRepository;
import com.pixous.hrportal.modules.notification.NotificationResponse;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveService leaveService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;
    private final NotificationRepository notificationRepository;
    private final com.pixous.hrportal.modules.org.DepartmentRepository departmentRepository;
    private final com.pixous.hrportal.modules.org.DesignationRepository designationRepository;
    private final com.pixous.hrportal.modules.user.OffboardingRecordRepository offboardingRecordRepository;
    /** Payroll cost on the executive dashboard is summed from real payslips. */
    private final com.pixous.hrportal.modules.payroll.PayslipRepository payslipRepository;

    /** Probation runs six months from joining unless a date says otherwise. */
    private static final int DEFAULT_PROBATION_MONTHS = 6;
    /** A confirmation counts as coming up when it falls inside this window. */
    private static final int CONFIRMATION_WINDOW_DAYS = 45;

    /**
     * The organisation at a glance for the admin / HR dashboard, narrowed to one
     * industry when asked. Everything is derived from records already kept —
     * nothing here needs a new entry from anybody to start reading true.
     */
    @Transactional(readOnly = true)
    public com.pixous.hrportal.modules.dashboard.dto.OrgInsights orgInsights(String industry) {
        String want = industry == null || industry.isBlank() || "ALL".equalsIgnoreCase(industry)
                ? null : industry.trim();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        List<User> everyone = userRepository.findByEnabledTrue().stream()
                .filter(u -> want == null || want.equalsIgnoreCase(u.getIndustry()))
                .toList();
        // "On the staff" for a headcount means not offboarded.
        List<User> active = everyone.stream().filter(u -> !isGone(u)).toList();
        List<User> gone = everyone.stream().filter(DashboardService::isGone).toList();

        // ---- who has just joined ----
        List<User> joinedThisMonth = active.stream()
                .filter(u -> u.getDateOfJoining() != null
                        && !u.getDateOfJoining().isBefore(monthStart)
                        && !u.getDateOfJoining().isAfter(today))
                .sorted(java.util.Comparator.comparing(User::getDateOfJoining).reversed())
                .toList();
        long joinedToday = joinedThisMonth.stream()
                .filter(u -> today.equals(u.getDateOfJoining())).count();

        // ---- probation, and whose confirmation is due ----
        List<User> probation = active.stream().filter(DashboardService::isOnProbation).toList();
        List<User> confirmations = probation.stream()
                .filter(u -> {
                    LocalDate ends = probationEnd(u);
                    if (ends == null) return false;
                    long days = java.time.temporal.ChronoUnit.DAYS.between(today, ends);
                    // Already due counts as coming up: it still needs doing.
                    return days <= CONFIRMATION_WINDOW_DAYS;
                })
                .sorted(java.util.Comparator.comparing(
                        u -> probationEnd(u), java.util.Comparator.nullsLast(LocalDate::compareTo)))
                .toList();

        // ---- today's attendance, broken down ----
        java.util.Set<Long> activeIds = active.stream().map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Attendance> todays = attendanceRepository.findByWorkDate(today).stream()
                .filter(a -> activeIds.contains(a.getUserId()))
                .toList();
        java.time.LocalTime officeEnd = java.time.LocalTime.of(18, 0);

        long wfh = todays.stream().filter(a -> "WFH".equalsIgnoreCase(a.getStatus())).count();
        long present = todays.stream()
                .filter(a -> a.getPunchInAt() != null)
                .filter(a -> !"ABSENT".equalsIgnoreCase(a.getStatus()))
                .count();
        long late = todays.stream().filter(a -> a.getLateMinutes() > 0 || a.isLate()).count();
        long earlyOut = todays.stream()
                .filter(a -> a.getPunchOutAt() != null
                        && a.getPunchOutAt().toLocalTime().isBefore(officeEnd))
                .count();
        java.util.Set<Long> marked = todays.stream().map(Attendance::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        long notMarked = active.stream().filter(u -> !marked.contains(u.getId())).count();

        // ---- how the company is distributed ----
        java.util.Map<Long, String> deptNames = departmentRepository.findByActiveTrueOrderByNameAsc()
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.pixous.hrportal.modules.org.Department::getId,
                        com.pixous.hrportal.modules.org.Department::getName, (a, b) -> a));
        java.util.Map<Long, String> desigNames = designationRepository.findByActiveTrueOrderByNameAsc()
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.pixous.hrportal.modules.org.Designation::getId,
                        com.pixous.hrportal.modules.org.Designation::getName, (a, b) -> a));

        java.util.Map<String, Long> byDepartment = countBy(active, u ->
                u.getDepartmentTitle() != null && !u.getDepartmentTitle().isBlank()
                        ? u.getDepartmentTitle().trim()
                        : deptNames.getOrDefault(u.getDepartmentId(), "Unassigned"));
        // Team is the designation title people actually carry; designation is the
        // master list entry. They differ often enough to be worth showing apart.
        java.util.Map<String, Long> byTeam = countBy(active, u ->
                u.getDesignationTitle() != null && !u.getDesignationTitle().isBlank()
                        ? u.getDesignationTitle().trim() : "No team");
        java.util.Map<String, Long> byDesignation = countBy(active, u ->
                desigNames.getOrDefault(u.getDesignationId(),
                        u.getDesignationTitle() != null && !u.getDesignationTitle().isBlank()
                                ? u.getDesignationTitle().trim() : "Unassigned"));

        // ---- joins and exits, month by month over the last year ----
        // An exit belongs to the month somebody actually left, which is the
        // relieving date on their offboarding record.
        java.util.Map<Long, LocalDate> relievedOn = new java.util.HashMap<>();
        gone.forEach(u -> offboardingRecordRepository.findByUserId(u.getId())
                .ifPresent(r -> relievedOn.put(u.getId(), r.getRelievingDate())));

        List<java.util.Map<String, Object>> growth = new java.util.ArrayList<>();
        for (int back = 11; back >= 0; back--) {
            java.time.YearMonth ym = java.time.YearMonth.from(today).minusMonths(back);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            long joined = everyone.stream().filter(u -> inRange(u.getDateOfJoining(), from, to)).count();
            long exited = gone.stream().filter(u -> inRange(relievedOn.get(u.getId()), from, to)).count();
            growth.add(new java.util.LinkedHashMap<>(java.util.Map.of(
                    "month", ym.getMonth().getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + ym.getYear(),
                    "joined", joined,
                    "exited", exited)));
        }

        return new com.pixous.hrportal.modules.dashboard.dto.OrgInsights(
                joinedToday, joinedThisMonth.size(), wfh, probation.size(), gone.size(),
                confirmations.size(),
                present, late, earlyOut, notMarked,
                people(joinedThisMonth, today, User::getDateOfJoining),
                people(probation, today, DashboardService::probationEnd),
                // The list says when each of them was relieved, not when they joined.
                people(gone, today, u -> relievedOn.get(u.getId())),
                people(confirmations, today, DashboardService::probationEnd),
                byDepartment, byTeam, byDesignation, growth);
    }

    private static boolean isGone(User u) {
        return "OFFBOARDED".equalsIgnoreCase(u.getProfileStatus());
    }

    /** Probation is what the employment type says, or a date still ahead. */
    private static boolean isOnProbation(User u) {
        if ("PROBATION".equalsIgnoreCase(u.getEmploymentType())) return true;
        LocalDate ends = u.getProbationEndDate();
        return ends != null && !ends.isBefore(LocalDate.now());
    }

    /** The recorded probation end, else six months from joining, else null. */
    private static LocalDate probationEnd(User u) {
        if (u.getProbationEndDate() != null) return u.getProbationEndDate();
        return u.getDateOfJoining() == null
                ? null : u.getDateOfJoining().plusMonths(DEFAULT_PROBATION_MONTHS);
    }


    private static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    private static java.util.Map<String, Long> countBy(
            List<User> users, java.util.function.Function<User, String> key) {
        return users.stream()
                .collect(java.util.stream.Collectors.groupingBy(key,
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static List<com.pixous.hrportal.modules.dashboard.dto.OrgInsights.Person> people(
            List<User> users, LocalDate today, java.util.function.Function<User, LocalDate> dateOf) {
        return users.stream().limit(50).map(u -> {
            LocalDate d = dateOf.apply(u);
            return new com.pixous.hrportal.modules.dashboard.dto.OrgInsights.Person(
                    u.getId(), u.getName(), u.getEmployeeCode(),
                    u.getDesignationTitle(), u.getPhotoPath(),
                    d == null ? null : d.toString(),
                    d == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(today, d));
        }).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeDashboard employee(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        LocalDate today = LocalDate.now();

        Attendance att = attendanceRepository.findByUserIdAndWorkDate(userId, today).orElse(null);

        var leaveBalances = leaveService.balances(userId, today.getYear());
        long pendingLeaves = leaveRequestRepository
                .findByUserIdAndStatus(userId, "PENDING").size();
        long openTickets = ticketRepository.countByRaisedByAndStatusNot(userId, "CLOSED");
        long myAssets = assetRepository.findByAssignedTo(userId).size();

        List<NotificationResponse> recent = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5))
                .map(NotificationResponse::from)
                .getContent();

        return new EmployeeDashboard(
                user.getName(),
                user.getEmployeeCode(),
                att != null && att.getPunchInAt() != null,
                att != null ? att.getPunchInAt() : null,
                att != null ? att.getPunchOutAt() : null,
                att != null ? att.getWorkedMinutes() : null,
                leaveBalances,
                pendingLeaves,
                openTickets,
                myAssets,
                recent);
    }

    @Transactional(readOnly = true)
    public ExecutiveDashboard executive(String industry) {
        boolean hasFilter = industry != null && !industry.trim().isEmpty();
        String filterVal = hasFilter ? industry.trim() : null;

        // Index every employee by id once so the per-record filters below do not
        // hit the database repeatedly. "" means no industry recorded.
        List<User> users = userRepository.findAll();
        java.util.Map<Long, String> industryByUser = users.stream()
                .collect(java.util.stream.Collectors.toMap(
                        User::getId,
                        u -> u.getIndustry() == null ? "" : u.getIndustry(),
                        (a, b) -> a));

        // Does a record's owner fall inside the selected industry (Overall = all)?
        java.util.function.Predicate<Long> inFilter = userId -> {
            if (filterVal == null) return true;
            String ind = industryByUser.get(userId);
            return ind != null && filterVal.equalsIgnoreCase(ind);
        };

        // Currently-working headcount: exclude offboarded staff so "Total" and the
        // attendance percentage match what the admin dashboard shows.
        java.util.Set<Long> activeUserIds = users.stream()
                .filter(u -> !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        long headcount = users.stream()
                .filter(u -> !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                .filter(u -> filterVal == null || filterVal.equalsIgnoreCase(u.getIndustry()))
                .count();
        LocalDate today = LocalDate.now();

        long presentToday = attendanceRepository.findByWorkDate(today).stream()
                .filter(a -> a.getPunchInAt() != null)
                .filter(a -> activeUserIds.contains(a.getUserId()))
                .filter(a -> inFilter.test(a.getUserId()))
                .count();
        double pct = headcount == 0 ? 0.0 : BigDecimal.valueOf(presentToday * 100.0 / headcount)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();

        long pendingApprovals = leaveRequestRepository.findAll().stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .filter(r -> inFilter.test(r.getUserId()))
                .count();

        long openTickets = ticketRepository.findAll().stream()
                .filter(t -> !"CLOSED".equals(t.getStatus()))
                .filter(t -> inFilter.test(t.getRaisedBy()))
                .count();

        long assigned = assetRepository.countByStatus("ASSIGNED");
        long inStock = assetRepository.countByStatus("IN_STOCK");

        // The four figures below were hard-coded: fifteen people in Engineering,
        // eight in a Sales department that does not exist, 95% attendance while
        // the attendance table was empty, and fifteen lakh of monthly payroll from
        // nowhere. They sat beside the real headcount with nothing to tell them
        // apart, on the screen a director reads. All four are computed now, and
        // where there is no data they come back empty rather than invented.

        // People per department, active staff only, honouring the industry filter.
        java.util.Map<String, Long> departmentBreakdown = users.stream()
                .filter(u -> !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                .filter(u -> filterVal == null || filterVal.equalsIgnoreCase(u.getIndustry()))
                .filter(u -> u.getDepartmentTitle() != null && !u.getDepartmentTitle().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        User::getDepartmentTitle,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));

        // Present and absent for each of the last six months, counted a day at a
        // time against the working headcount. Absent is "expected but no punch",
        // so it can never go negative when somebody joins mid-month.
        java.util.List<java.util.Map<String, Object>> monthlyAttendanceTrend = new java.util.ArrayList<>();
        java.time.format.DateTimeFormatter monthLabel =
                java.time.format.DateTimeFormatter.ofPattern("MMM");
        for (int back = 5; back >= 0; back--) {
            LocalDate monthStart = today.minusMonths(back).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            if (monthEnd.isAfter(today)) monthEnd = today;

            long present = 0;
            long workingDays = 0;
            for (LocalDate d = monthStart; !d.isAfter(monthEnd); d = d.plusDays(1)) {
                // Saturday is a weekend. Counting it inflated the expected
                // attendance and made every month's rate look worse than it was.
                if (com.pixous.hrportal.common.WorkCalendar.isWeekend(d)) continue;
                workingDays++;
                present += attendanceRepository.findByWorkDate(d).stream()
                        .filter(a -> a.getPunchInAt() != null)
                        .filter(a -> activeUserIds.contains(a.getUserId()))
                        .filter(a -> inFilter.test(a.getUserId()))
                        .count();
            }
            long expected = workingDays * headcount;
            monthlyAttendanceTrend.add(java.util.Map.of(
                    "month", monthStart.format(monthLabel),
                    "present", present,
                    "absent", Math.max(0, expected - present)));
        }

        // Days actually taken, by leave type, from approved requests.
        java.util.Map<Long, String> leaveTypeNames = leaveService.types().stream()
                .collect(java.util.stream.Collectors.toMap(
                        t -> t.id(), t -> t.name(), (a, b) -> a));
        java.util.Map<String, Long> leaveUtilization = leaveRequestRepository.findAll().stream()
                .filter(r -> "APPROVED".equalsIgnoreCase(r.getStatus()))
                .filter(r -> inFilter.test(r.getUserId()))
                .filter(r -> r.getWorkingDays() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> leaveTypeNames.getOrDefault(r.getLeaveTypeId(), "Other"),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.summingLong(r -> r.getWorkingDays().longValue())));

        // Payroll cost per month, from payslips that were actually generated.
        // Empty until the first run — which is the honest answer, and what the
        // chart should show rather than a number nobody can trace.
        java.util.Map<java.time.YearMonth, BigDecimal> costByMonth = payslipRepository.findAll().stream()
                .filter(p -> p.getGrossSalary() != null && p.getPayYear() != null && p.getPayMonth() != null)
                .filter(p -> inFilter.test(p.getUserId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> java.time.YearMonth.of(p.getPayYear(), p.getPayMonth()),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.reducing(
                                BigDecimal.ZERO,
                                com.pixous.hrportal.modules.payroll.Payslip::getGrossSalary,
                                BigDecimal::add)));
        java.util.List<java.util.Map.Entry<java.time.YearMonth, BigDecimal>> costEntries =
                new java.util.ArrayList<>(costByMonth.entrySet());
        if (costEntries.size() > 6) {
            costEntries = costEntries.subList(costEntries.size() - 6, costEntries.size());
        }
        java.util.List<java.util.Map<String, Object>> payrollCosts = costEntries.stream()
                .<java.util.Map<String, Object>>map(e -> java.util.Map.of(
                        "month", e.getKey().format(monthLabel),
                        "cost", e.getValue()))
                .toList();

        return new ExecutiveDashboard(
                headcount, presentToday, pct, pendingApprovals, openTickets, assigned, inStock,
                departmentBreakdown, monthlyAttendanceTrend, leaveUtilization, payrollCosts);
    }

    /** Upcoming birthdays + work anniversaries in the next 30 days (any employee can view). */
    @Transactional(readOnly = true)
    public List<com.pixous.hrportal.modules.dashboard.dto.Celebration> celebrations() {
        return celebrations(null);
    }

    /**
     * Upcoming celebrations, optionally for one industry only. The filter runs
     * before the twelve-row limit, so narrowing to Infra shows twelve Infra
     * people rather than whatever survived an org-wide cut.
     */
    @Transactional(readOnly = true)
    public List<com.pixous.hrportal.modules.dashboard.dto.Celebration> celebrations(String industry) {
        String want = industry == null || industry.isBlank() || "ALL".equalsIgnoreCase(industry)
                ? null : industry.trim();
        LocalDate today = LocalDate.now();
        List<com.pixous.hrportal.modules.dashboard.dto.Celebration> out = new java.util.ArrayList<>();
        for (User u : userRepository.findByEnabledTrue()) {
            if ("OFFBOARDED".equalsIgnoreCase(u.getProfileStatus())) continue;
            if (want != null && !want.equalsIgnoreCase(u.getIndustry())) continue;
            addOccurrence(out, u, u.getDob(), "BIRTHDAY", today, false);
            addOccurrence(out, u, u.getDateOfJoining(), "ANNIVERSARY", today, true);
        }
        out.sort(java.util.Comparator.comparingInt(com.pixous.hrportal.modules.dashboard.dto.Celebration::daysUntil));
        return out.stream().limit(12).toList();
    }

    /** Only the celebrations that fall exactly today — used by the daily notification job. */
    @Transactional(readOnly = true)
    public List<com.pixous.hrportal.modules.dashboard.dto.Celebration> todaysCelebrations() {
        return celebrations().stream()
                .filter(c -> c.daysUntil() == 0)
                .toList();
    }

    private void addOccurrence(List<com.pixous.hrportal.modules.dashboard.dto.Celebration> out, User u,
                               LocalDate base, String type, LocalDate today, boolean anniversary) {
        if (base == null) return;
        LocalDate next;
        try {
            next = base.withYear(today.getYear());
        } catch (Exception e) { // Feb 29 → treat as Mar 1
            next = LocalDate.of(today.getYear(), base.getMonthValue(), 1).plusMonths(1);
        }
        if (next.isBefore(today)) next = next.plusYears(1);
        int daysUntil = (int) java.time.temporal.ChronoUnit.DAYS.between(today, next);
        // Two months ahead: the dashboard panel shows what is coming, and a
        // month is not enough notice for an anniversary worth marking.
        if (daysUntil > 60) return;
        Integer years = anniversary ? Math.max(1, next.getYear() - base.getYear()) : null;
        if (anniversary && next.getYear() - base.getYear() < 1) return; // not yet 1 year
        out.add(new com.pixous.hrportal.modules.dashboard.dto.Celebration(
                u.getId(), u.getName(), u.getEmployeeCode(),
                u.getDesignationTitle(), u.getPhotoPath(), type, next, daysUntil, years));
    }
}
