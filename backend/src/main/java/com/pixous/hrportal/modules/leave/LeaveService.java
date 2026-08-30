package com.pixous.hrportal.modules.leave;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.leave.dto.BulkLeaveDecisionRequest;
import com.pixous.hrportal.modules.leave.dto.LeaveApplyRequest;
import com.pixous.hrportal.modules.leave.dto.LeaveBalanceResponse;
import com.pixous.hrportal.modules.leave.dto.LeaveDecisionRequest;
import com.pixous.hrportal.modules.leave.dto.LeaveRequestResponse;
import com.pixous.hrportal.modules.leave.dto.LeaveTypeRequest;
import com.pixous.hrportal.modules.leave.dto.LeaveTypeResponse;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.org.Holiday;
import com.pixous.hrportal.modules.org.HolidayRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveTypeRepository typeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final HolidayRepository holidayRepository;
    private final com.pixous.hrportal.modules.attendance.AttendanceRepository attendanceRepository;
    private final NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    // ---------- Reference data ----------

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> types() {
        // Scoped to the caller's company here, in the open, rather than relying on
        // the Hibernate tenant filter — which did not apply on this path. A
        // company-1 account was being shown company-4's leave types.
        //
        // A type with no company is shared; nothing is stored that way today, but
        // a row created outside a request should not vanish. A caller without a
        // company of their own (technical admin) sees everything.
        Long mine = com.pixous.hrportal.security.SecurityUtils.currentCompanyId();
        return typeRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(t -> mine == null || t.getCompanyId() == null || mine.equals(t.getCompanyId()))
                .map(LeaveTypeResponse::from).toList();
    }

    @Transactional
    public LeaveTypeResponse createType(LeaveTypeRequest req) {
        LeaveType existing = req.code() == null ? null
                : typeRepository.findByCodeIgnoreCase(req.code().trim()).orElse(null);
        // A code must be unique. If a type with this code was soft-deleted,
        // reactivate and update it instead of failing on the DB constraint.
        if (existing != null) {
            if (existing.isActive()) {
                throw ApiException.business("A leave type with code \"" + req.code() + "\" already exists");
            }
            existing.setActive(true);
            updateTypeFromReq(existing, req);
            return LeaveTypeResponse.from(typeRepository.save(existing));
        }
        LeaveType t = new LeaveType();
        updateTypeFromReq(t, req);
        return LeaveTypeResponse.from(typeRepository.save(t));
    }

    @Transactional
    public LeaveTypeResponse updateType(Long id, LeaveTypeRequest req) {
        LeaveType t = typeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leave type"));
        if (req.code() != null) {
            typeRepository.findByCodeIgnoreCase(req.code().trim())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw ApiException.business("A leave type with code \"" + req.code() + "\" already exists");
                    });
        }
        updateTypeFromReq(t, req);
        return LeaveTypeResponse.from(typeRepository.save(t));
    }

    @Transactional
    public void deleteType(Long id) {
        LeaveType t = typeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leave type"));
        t.setActive(false);
        typeRepository.save(t);
    }

    private void updateTypeFromReq(LeaveType t, LeaveTypeRequest req) {
        t.setName(req.name());
        t.setCode(req.code());
        t.setMaxDaysPerYear(req.maxDaysPerYear());
        t.setCarryForward(req.carryForward());
        t.setEncashable(req.encashable());
        t.setGenderRestriction(
    req.genderRestriction() != null && !req.genderRestriction().isBlank()
        ? req.genderRestriction().charAt(0)
        : null
);
        t.setAllowPastDates(req.allowPastDates());
        t.setAccrualType(req.accrualType());
        t.setMinNoticeDays(req.minNoticeDays());
        t.setMonthlyLimit(req.monthlyLimit());
        t.setPaid(req.paid());
    }

    /**
     * Loss-of-Pay preview for a payslip: the number of UNPAID approved leave
     * working-days a user took in a month, and the number of working days
     * (Mon–Fri, excluding holidays) in that month for the per-day divisor.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> lopPreview(Long userId, int year, int month) {
        java.util.Set<Long> paidTypeIds = typeRepository.findAll().stream()
                .filter(LeaveType::isPaid).map(LeaveType::getId).collect(Collectors.toSet());
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        BigDecimal unpaidDays = BigDecimal.ZERO;
        BigDecimal paidDays = BigDecimal.ZERO;
        int leaveCount = 0;
        for (LeaveRequest r : requestRepository.findByUserIdAndStatus(userId, "APPROVED")) {
            if (r.getFromDate() == null) continue;
            if (r.getFromDate().getYear() != year || r.getFromDate().getMonthValue() != month) continue;
            leaveCount++;
            BigDecimal days = r.getWorkingDays() == null ? BigDecimal.ZERO : r.getWorkingDays();
            if (paidTypeIds.contains(r.getLeaveTypeId())) {
                paidDays = paidDays.add(days);      // paid leave — no deduction
            } else {
                unpaidDays = unpaidDays.add(days);
            }
        }
        // Working days in the month = weekdays minus holidays.
        //
        // Saturday counts as a weekend now, so a month is roughly twenty-two days
        // rather than twenty-six. This is the divisor the payslip's per-day rate
        // is built from, and it is also what "absent" is measured against — a
        // Saturday nobody punched used to be an absence, and each of those
        // deducted a day's pay for a day nobody was asked to work.
        Set<LocalDate> holidays = holidayRepository.findAll().stream()
                .map(Holiday::getHolidayDate).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        int workingDays = 0;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate day = ym.atDay(d);
            if (com.pixous.hrportal.common.WorkCalendar.isWeekend(day)) continue;
            if (holidays.contains(day)) continue;
            workingDays++;
        }
        // Days actually attended in the month, so what is left over -- neither
        // worked nor covered by leave -- is what was missed.
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        long present = attendanceRepository
                .countByUserIdAndWorkDateBetweenAndStatus(userId, first, last, "PRESENT")
                + attendanceRepository
                .countByUserIdAndWorkDateBetweenAndStatus(userId, first, last, "WFH");
        BigDecimal leaveDays = paidDays.add(unpaidDays);
        int absent = (int) Math.max(0, workingDays - present - leaveDays.intValue());

        // A day neither worked nor covered by approved leave is a day not paid for.
        // Counting only unpaid leave meant somebody absent for a whole month with no
        // leave applied showed a Loss of Pay of zero — which is what the payslip
        // then deducted.
        BigDecimal deductibleDays = unpaidDays.add(BigDecimal.valueOf(absent));

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("unpaidLeaveDays", unpaidDays);
        out.put("paidLeaveDays", paidDays);
        out.put("totalLeaveDays", leaveDays);
        out.put("leaveRequestCount", leaveCount);
        out.put("presentDays", present);
        out.put("absentDays", absent);
        out.put("workingDaysInMonth", workingDays);
        // What Loss of Pay should be built from, and its two parts, so a payslip
        // can show the working rather than a number somebody has to trust.
        out.put("deductibleDays", deductibleDays);
        out.put("lopFromUnpaidLeave", unpaidDays);
        out.put("lopFromAbsence", absent);
        return out;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> balances(Long userId, Integer year) {
        int y = year != null ? year : Year.now().getValue();
        Map<Long, LeaveType> typeMap = typeRepository.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, t -> t));
        return balanceRepository.findByUserIdAndYear(userId, y).stream()
                .map(b -> {
                    LeaveType t = typeMap.get(b.getLeaveTypeId());
                    return new LeaveBalanceResponse(
                            b.getLeaveTypeId(),
                            t != null ? t.getName() : "?",
                            t != null ? t.getCode() : "?",
                            b.getYear(), b.getAllocated(), b.getUsed(), b.getAvailable());
                }).toList();
    }

    /**
     * One-click bulk allocation: give every enabled employee their annual leave
     * balance for {@code year}, using each active leave type's configured
     * "max days per year" as the allocated amount. Types with no cap or a zero
     * cap (e.g. LOP, Comp-Off) are skipped. Existing balances are left untouched
     * (never overwrites a used/allocated value), so it is safe to run repeatedly.
     *
     * @return how many new balance rows were created and how many employees were covered.
     */
    @Transactional
    public Map<String, Integer> allocateDefaultsToAll(Integer year) {
        int y = year != null ? year : Year.now().getValue();
        // Balances are only ever handed out for this year or one still ahead;
        // a year already gone by cannot be allocated.
        int thisYear = Year.now().getValue();
        if (y < thisYear) {
            throw ApiException.business("Leave cannot be allocated for " + y
                    + " — pick " + thisYear + " or a later year.");
        }
        List<User> users = userRepository.findByEnabledTrue();
        List<LeaveType> types = typeRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(t -> t.getMaxDaysPerYear() != null && t.getMaxDaysPerYear() > 0)
                .toList();

        int created = 0;
        for (User u : users) {
            for (LeaveType t : types) {
                boolean exists = balanceRepository
                        .findByUserIdAndLeaveTypeIdAndYear(u.getId(), t.getId(), y)
                        .isPresent();
                if (exists) continue;
                LeaveBalance b = new LeaveBalance();
                b.setUserId(u.getId());
                b.setLeaveTypeId(t.getId());
                b.setYear(y);
                b.setAllocated(BigDecimal.valueOf(t.getMaxDaysPerYear()));
                b.setUsed(BigDecimal.ZERO);
                balanceRepository.save(b);
                created++;
            }
        }
        return Map.of("created", created, "employees", users.size(), "year", y);
    }

    /** Admin: reset an employee's leave — zero all balances and clear their
     *  leave request history. */
    @Transactional
    public void resetUserLeave(Long userId) {
        balanceRepository.findByUserId(userId).forEach(b -> {
            b.setUsed(BigDecimal.ZERO);
            balanceRepository.save(b);
        });
        requestRepository.deleteAll(requestRepository.findByUserId(userId));
    }

    // ---------- Apply ----------

    @Transactional
    public LeaveRequestResponse apply(Long userId, LeaveApplyRequest req) {
        if (req.toDate().isBefore(req.fromDate())) {
            throw ApiException.business("End date cannot be before start date");
        }
        LeaveType type = typeRepository.findById(req.leaveTypeId())
                .orElseThrow(() -> ApiException.notFound("Leave type"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        // Gender restriction (e.g. maternity = 'F')  [US-IT-EMP-03 AC3]
       if (type.getGenderRestriction() != null && user.getGender() != null) {
    String leaveGender = String.valueOf(type.getGenderRestriction()).toUpperCase();
    String userGender = String.valueOf(user.getGender()).toUpperCase();

    if (!leaveGender.equals(userGender)) {
        throw ApiException.business(type.getName() + " is not applicable for your profile");
    }
}

        // Past-date guard unless the type explicitly allows it (sick leave)
        if (!type.isAllowPastDates() && req.fromDate().isBefore(LocalDate.now())) {
            throw ApiException.business("This leave type cannot be applied for past dates");
        }

        // Note: the per-month cap for CL/SL is enforced below via
        // leave_types.monthly_limit + countMonthlyConsuming (1 CL + 1 SL / month).
        // A separate 3-month-gap rule used to live here; it contradicted the
        // monthly cap (blocking the 2nd allowed month) and has been removed.

        // Minimum-notice guard (civil min-notice rule)
        if (type.getMinNoticeDays() != null && type.getMinNoticeDays() > 0) {
            long noticeDays = LocalDate.now().until(req.fromDate()).getDays()
                    + LocalDate.now().until(req.fromDate()).getMonths() * 30L;
            if (req.fromDate().isAfter(LocalDate.now())
                    && noticeDays < type.getMinNoticeDays()) {
                throw ApiException.business(
                        type.getName() + " requires at least " + type.getMinNoticeDays() + " day(s) notice");
            }
        }

        /*
         * One leave per person per day, whatever its type.
         *
         * Somebody who is on Sick Leave on the 27th cannot also be on Casual
         * Leave on the 27th -- they are one person and it is one day. Nothing
         * stopped that before: the quarterly cap and the notice period are
         * per-type, so two different types on the same date passed every
         * check and produced two leave records for one absence, each
         * deducting from a different balance.
         *
         * Checked against APPROVED and PENDING only. A rejected or cancelled
         * request never consumed the day, so it must not block a fresh one.
         */
        List<LeaveRequest> clashes = requestRepository.findOverlapping(
                userId, req.fromDate(), req.toDate(), null);
        if (!clashes.isEmpty()) {
            LeaveRequest first = clashes.get(0);
            String typeName = typeRepository.findById(first.getLeaveTypeId())
                    .map(LeaveType::getName).orElse("leave");
            String when = first.getFromDate().equals(first.getToDate())
                    ? "on " + first.getFromDate()
                    : "from " + first.getFromDate() + " to " + first.getToDate();
            throw ApiException.business(
                    "You already have " + typeName + " " + when
                            + " (" + first.getStatus().toLowerCase() + "). "
                            + "Only one leave per day is allowed, whatever the type. "
                            + "Cancel that request first, or choose other dates.");
        }

        /*
         * A leave that starts or ends on a weekend is a mistake, not a request.
         *
         * countWorkingDays below already ignores Saturdays and Sundays, so
         * picking Saturday to Sunday produced a request for zero days and the
         * message underneath said only "no working days" -- true, but it did
         * not say why, and a range that merely begins on a Saturday counted
         * correctly while still recording a start date nobody works on.
         *
         * Said plainly here instead, naming the day, so the person can see
         * what to change.
         */
        if (com.pixous.hrportal.common.WorkCalendar.isWeekend(req.fromDate())) {
            throw ApiException.business(
                    "Leave cannot start on a "
                            + dayName(req.fromDate())
                            + ". Saturdays and Sundays are not working days — "
                            + "choose a weekday.");
        }
        if (com.pixous.hrportal.common.WorkCalendar.isWeekend(req.toDate())) {
            throw ApiException.business(
                    "Leave cannot end on a "
                            + dayName(req.toDate())
                            + ". Saturdays and Sundays are not working days — "
                            + "choose a weekday.");
        }

        BigDecimal workingDays = BigDecimal.valueOf(
                countWorkingDays(req.fromDate(), req.toDate()));
        if (workingDays.signum() <= 0) {
            throw ApiException.business(
                    "That range has no working days in it — every day in it is a "
                            + "weekend or a public holiday.");
        }

        // Quarterly cap: Casual & Sick leave are limited to 1 per 3-month quarter
        // (so 4 per year). monthly_limit (=1) is used as the per-quarter allowance.
        if (type.getMonthlyLimit() != null && type.getMonthlyLimit() > 0) {
            LocalDate d = req.fromDate();
            int qStartMonth = ((d.getMonthValue() - 1) / 3) * 3 + 1;
            LocalDate qStart = LocalDate.of(d.getYear(), qStartMonth, 1);
            LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
            long usedThisQuarter = requestRepository.countRequestsInRange(
                    userId, type.getId(), qStart, qEnd);
            if (usedThisQuarter >= type.getMonthlyLimit()) {
                throw ApiException.business(
                        "No " + type.getName() + " left: only " + type.getMonthlyLimit()
                                + " " + type.getName() + " allowed per 3 months. "
                                + "Next available from " + qEnd.plusDays(1) + ".");
            }
        }

        /*
         * Casual and Sick Leave carry a three-month gap, counted from the last day
         * actually taken rather than by calendar quarter. The quarterly cap above
         * would let 31 March and 1 April stand as two separate quarters — one day
         * apart — which is not what "one every three months" means.
         *
         * Sick Leave had only the calendar-quarter cap above, so the gap it
         * was meant to keep could be a single day: one taken on 31 March and
         * another on 1 April fall in different quarters and both passed. The
         * two types carry the same allowance and the same rule, and there was
         * no reason for them to be enforced differently.
         */
        if ("CL".equalsIgnoreCase(type.getCode()) || "SL".equalsIgnoreCase(type.getCode())) {
            LocalDate lastTaken = requestRepository.findLatestDayTaken(userId, type.getId());
            if (lastTaken != null) {
                LocalDate availableFrom = lastTaken.plusMonths(3).plusDays(1);
                if (req.fromDate().isBefore(availableFrom)) {
                    throw ApiException.business(
                            type.getName() + " can only be taken once every three months. "
                                    + "Your last one ran to "
                                    + lastTaken.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                    + ", so the next can start on or after "
                                    + availableFrom.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                    + ".");
                }
            }
        }

        // Balance check (LOP-type leaves have no allocation and are skipped)
        int year = req.fromDate().getYear();
        if (!"LOP".equalsIgnoreCase(type.getCode())) {
            LeaveBalance balance = balanceRepository
                    .findByUserIdAndLeaveTypeIdAndYear(userId, type.getId(), year)
                    .orElse(null);
            if (balance == null) {
                throw ApiException.business("No leave balance allocated for " + type.getName());
            }
            if (balance.getAvailable().compareTo(workingDays) < 0) {
                throw ApiException.business("Insufficient balance: available "
                        + balance.getAvailable() + ", requested " + workingDays);
            }
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setUserId(userId);
        lr.setLeaveTypeId(type.getId());
        lr.setFromDate(req.fromDate());
        lr.setToDate(req.toDate());
        lr.setWorkingDays(workingDays);
        lr.setReason(req.reason());
        lr.setAttachmentPath(req.attachmentPath());
        lr.setRequestedTo(req.requestedTo());
        lr.setStatus("PENDING");
        LeaveRequest saved = requestRepository.save(lr);

        // Notify the approver. If the employee chose a specific "Request to"
        // approver, only that person is alerted (+SMS); otherwise fall back to
        // every leave approver.
        String label = type.getName() + " (" + workingDays + " day(s), "
                + req.fromDate() + (req.fromDate().equals(req.toDate()) ? "" : " to " + req.toDate()) + ")";
        List<User> approvers;
        if (req.requestedTo() != null) {
            approvers = userRepository.findById(req.requestedTo()).map(List::of).orElseGet(List::of);
        } else {
            approvers = userRepository.findByPermission("LEAVE_APPROVE");
        }
        for (User approver : approvers) {
            notificationService.createAndPush(
                    approver.getId(),
                    "Leave request pending",
                    user.getName() + " applied for " + label,
                    "LEAVE",
                    "/leave/approvals");
            if (approver.getPhone() != null && !approver.getPhone().isBlank()) {
                smsService.send(approver.getPhone(),
                        "Pixous HR: " + user.getName() + " applied for " + label
                                + ". Please review in the portal.");
            }
        }
        return LeaveRequestResponse.from(saved, user.getName(), type.getName());
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> myRequests(Long userId, int page, int size) {
        Map<Long, String> typeNames = typeNameMap();
        String name = userRepository.findById(userId).map(User::getName).orElse("?");
        Map<Long, User> users = userRepository.findAll().stream().collect(Collectors.toMap(User::getId, u -> u));
        Page<LeaveRequestResponse> result = requestRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(r -> {
                    String toName = r.getRequestedTo() != null && users.containsKey(r.getRequestedTo())
                            ? users.get(r.getRequestedTo()).getName() : null;
                    String decName = r.getDecidedBy() != null && users.containsKey(r.getDecidedBy())
                            ? users.get(r.getDecidedBy()).getName() : null;
                    return LeaveRequestResponse.from(r, name, typeNames.getOrDefault(r.getLeaveTypeId(), "?"),
                            null, false, toName, decName);
                });
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> myQueue(Long userId) {
        Map<Long, String> typeNames = typeNameMap();
        String name = userRepository.findById(userId).map(User::getName).orElse("?");
        Map<Long, User> users = userRepository.findAll().stream().collect(Collectors.toMap(User::getId, u -> u));
        return requestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> {
                    String toName = r.getRequestedTo() != null && users.containsKey(r.getRequestedTo())
                            ? users.get(r.getRequestedTo()).getName() : null;
                    String decName = r.getDecidedBy() != null && users.containsKey(r.getDecidedBy())
                            ? users.get(r.getDecidedBy()).getName() : null;
                    return LeaveRequestResponse.from(r, name, typeNames.getOrDefault(r.getLeaveTypeId(), "?"),
                            null, false, toName, decName);
                }).toList();
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        LeaveRequest lr = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Leave request"));
        if (!lr.getUserId().equals(userId)) {
            throw ApiException.business("You can only cancel your own request");
        }
        if (!"PENDING".equals(lr.getStatus()) && !"APPROVED".equals(lr.getStatus())) {
            throw ApiException.business("Only pending or approved leave can be cancelled");
        }
        // Refund balance if it had been approved (and deducted)
        if ("APPROVED".equals(lr.getStatus())) {
            refundBalance(lr);
        }
        String previousStatus = lr.getStatus();
        lr.setStatus("CANCELLED");
        lr.setUpdatedAt(LocalDateTime.now());

        // Let whoever was handling the request know it is withdrawn.
        Long notify = lr.getRequestedTo() != null ? lr.getRequestedTo() : lr.getDecidedBy();
        if (notify != null && !notify.equals(userId)) {
            String who = userRepository.findById(userId).map(User::getName).orElse("An employee");
            String typeName = typeRepository.findById(lr.getLeaveTypeId())
                    .map(LeaveType::getName).orElse("leave");
            notificationService.createAndPush(notify,
                    "Leave cancelled",
                    who + " cancelled their " + ("APPROVED".equals(previousStatus) ? "approved " : "")
                            + typeName + " (" + lr.getFromDate() + " to " + lr.getToDate() + ")",
                    "LEAVE", "/leave/approvals");
        }
    }

    // ---------- Manager inbox + decisions ----------

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> pendingForManager(Long approverId) {
        Map<Long, String> typeNames = typeNameMap();
        User approver = userRepository.findById(approverId).orElse(null);
        if (approver == null) return List.of();
        boolean adminView = leaveHasRole(approver, "SUPER_ADMIN")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE");

        List<LeaveRequest> all = requestRepository.findAllPending();
        Map<Long, User> applicants = userRepository.findAllById(
                        all.stream().map(LeaveRequest::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<LeaveRequestResponse> out = new ArrayList<>();
        for (LeaveRequest r : all) {
            User applicant = applicants.get(r.getUserId());
            boolean canAct = canApproveLeave(approver, applicant, r);
            boolean isTeamMember = leaveHasRole(approver, "IT_TL") && sameTeam(approver, applicant);
            boolean isHR = leaveHasRole(approver, "IT_MGR") || leaveHasRole(approver, "IT_HR");
            // Admins and HR see the whole queue; TLs see their team members; other
            // approvers only see rows they can act on.
            if (!canAct && !adminView && !isHR && !isTeamMember) continue;
            out.add(LeaveRequestResponse.from(r,
                    applicant != null ? applicant.getName() : "?",
                    typeNames.getOrDefault(r.getLeaveTypeId(), "?"),
                    topLeaveRole(applicant), canAct));
        }
        return out;
    }

    /**
     * How a person's role reads on screen, for the name of whoever a request went
     * to or was decided by. Null when there is nobody, so the column simply shows
     * the dash it already showed.
     */
    private static String roleLabel(User u) {
        if (u == null) return null;
        if (leaveHasRole(u, "SUPER_ADMIN")) return "Admin";
        if (leaveHasRole(u, "IT_MGR") || leaveHasRole(u, "IT_HR") || leaveHasRole(u, "CV_HR")) return "HR";
        if (leaveHasRole(u, "IT_TL") || leaveHasRole(u, "CV_SUP")) return "Team Leader";
        return "Employee";
    }

    /** Highest role of a user for leave routing (MGR > TL > employee). */
    private static String topLeaveRole(User u) {
        if (u == null) return "IT_EMP";
        if (leaveHasRole(u, "SUPER_ADMIN")) return "SUPER_ADMIN";
        if (leaveHasRole(u, "IT_MGR")) return "IT_MGR";
        if (leaveHasRole(u, "IT_TL")) return "IT_TL";
        return "IT_EMP";
    }

    /**
     * Whether this person holds the role, treating COMPANY_ADMIN as SUPER_ADMIN.
     *
     * <p>A tenant company has no SUPER_ADMIN of its own — its top administrator is
     * COMPANY_ADMIN, the same job under the name the platform grew up with. Asked
     * literally, this returned false for that person, so leave routing put them
     * nowhere: they were not the "Admin" the approval chain escalates to, and the
     * approvals list they opened was an employee's own rather than the company's.
     * A company whose administrator was its only administrator had leave requests
     * with no one able to decide them.
     */
    private static boolean leaveHasRole(User u, String code) {
        if (u == null) return false;
        return u.getRoles().stream().anyMatch(r -> {
            String held = r.getCode();
            if (code.equals(held)) return true;
            return "SUPER_ADMIN".equals(code) && "COMPANY_ADMIN".equals(held);
        });
    }

    /**
     * Who may approve/reject a given leave request:
     *  - Employee 1–2 days  : their Team Leader (same designation) OR a Manager
     *  - Employee 3+ days    : Manager only (TL cannot)
     *  - Team Leader's leave : Manager only
     *  - Manager's leave     : Admin (SUPER_ADMIN) only
     * Never your own request.
     */
    /** Employee code of the one person who approves HR's own leave and acts as main HR. */
    private static final String HR_LEAVE_APPROVER_CODE = "HR0001";

    /**
     * Whether the approver {@code a} covers the applicant {@code b}'s team.
     *
     * <p>Called only with {@code a} already known to hold IT_TL, so the extra
     * assignment below is read in that direction: a leader may be given teams
     * beyond their own designation, which is how a team with no leader of its
     * own -- QA Testing -- gets one. With nothing assigned this behaves exactly
     * as it did: designation against designation.
     */
    private static boolean sameTeam(User a, User b) {
        String at = a.getDesignationTitle();
        String bt = b.getDesignationTitle();
        if (bt != null && com.pixous.hrportal.modules.user.ExtraTeams.leads(a.getId(), bt)) {
            return true;
        }
        return at != null && bt != null && at.trim().equalsIgnoreCase(bt.trim());
    }

    /**
     * Exactly one approver is valid for any given leave, so the picker and the
     * decision agree:
     *   employee, up to 3 days -> their own team's Team Leader
     *   employee, more than 3   -> HR
     *   Team Leader             -> HR
     *   HR                      -> the escalation approver (PIX-E100)
     */
    private boolean canActOnLeave(User approver, User applicant, java.math.BigDecimal workingDays) {
        if (approver.getId().equals(applicant.getId())) return false;
        if ("PIX-E100".equalsIgnoreCase(approver.getEmployeeCode()) || leaveHasRole(approver, "SUPER_ADMIN") || leaveHasRole(approver, "COMPANY_ADMIN")) {
            return true;
        }
        String who = topLeaveRole(applicant);
        double days = workingDays == null ? 0 : workingDays.doubleValue();

        if ("SUPER_ADMIN".equals(who)) return false;           // admin's own leave — not handled here
        if ("IT_MGR".equals(who) || "IT_HR".equals(who)) {
            // HR's leave escalates to Admin
            return leaveHasRole(approver, "SUPER_ADMIN");
        }
        if ("IT_TL".equals(who)) return HR_LEAVE_APPROVER_CODE.equalsIgnoreCase(approver.getEmployeeCode());

        // Plain employee: short leave stays inside the team, longer leave goes to
        // HR. Only one of the two can act, never both.
        if (days <= 3) {
            return leaveHasRole(approver, "IT_TL") && sameTeam(approver, applicant);
        }
        return HR_LEAVE_APPROVER_CODE.equalsIgnoreCase(approver.getEmployeeCode());
    }

    /** Who may act on this specific leave: the chosen approver if routed, else the day/role rule. */
    private boolean canApproveLeave(User approver, User applicant, LeaveRequest r) {
        if (approver == null) return false;

        /*
          The person the request names decides it, and nobody else.

          There was an override above this: the CTO and anyone holding
          SUPER_ADMIN or COMPANY_ADMIN could approve or reject any leave,
          whoever it had been addressed to. That made the chain optional -- an
          employee chose their Team Leader and somebody else decided it, and
          HR could be bypassed on a Team Leader's request.

          Everybody above still SEES the whole queue: the listing above keeps
          adminView and isHR, so nothing disappears from anybody's screen.
          Seeing is not deciding, and the two had been conflated.
        */
        if (r.getRequestedTo() != null) return r.getRequestedTo().equals(approver.getId());

        /*
          Only where a request names nobody does the rung decide it. Older rows
          predate the approver field, and a leave that can never be actioned is
          worse than one actioned by the right rung.
        */
        return applicant != null && canActOnLeave(approver, applicant, r.getWorkingDays());
    }

    /** Valid approvers an employee can address a leave of {@code days} to. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> leaveApprovers(Long userId, double days) {
        User me = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        boolean iAmHr = leaveHasRole(me, "IT_MGR") || leaveHasRole(me, "IT_HR") || leaveHasRole(me, "CV_HR");
        boolean iAmTl = leaveHasRole(me, "IT_TL") || leaveHasRole(me, "CV_SUP");

        /*
         * One rung up the ladder, and only one.
         *
         *   employee, up to 3 days  ->  their Team Leader
         *   employee, over 3 days   ->  HR
         *   team leader             ->  HR
         *   HR                      ->  the CTO
         *
         * The lists used to include the CTO and every administrator alongside
         * the right approver at nearly every rung, so an employee asking for
         * four days was offered HR, the CTO and anyone holding an admin role. A
         * chain that offers a choice is not a chain: requests skip the person
         * who actually knows whether the team can spare them.
         *
         * Each rung is now exactly one kind of person.
         */
        java.util.function.Predicate<User> isCto =
                u -> "PIX-E100".equalsIgnoreCase(u.getEmployeeCode());
        java.util.function.Predicate<User> isHr =
                u -> leaveHasRole(u, "IT_MGR") || leaveHasRole(u, "IT_HR") || leaveHasRole(u, "CV_HR");
        java.util.function.Predicate<User> isTl =
                u -> leaveHasRole(u, "IT_TL") || leaveHasRole(u, "CV_SUP");

        /*
         * The people whose job is actually HR.
         *
         * isHr above also counts IT_MGR, which is the manager role, so the HR
         * rung offered three names: HR itself plus two managers who hold
         * IT_MGR. A leave request should reach HR, not whoever happens to sit
         * near them on the role list.
         */
        java.util.function.Predicate<User> isRealHr =
                u -> leaveHasRole(u, "IT_HR") || leaveHasRole(u, "CV_HR");

        java.util.function.Predicate<User> allowed;
        boolean hrRung = false;
        if (iAmHr) {
            allowed = isCto;
        } else if (iAmTl) {
            allowed = isHr;
            hrRung = true;
        } else if (days <= 3) {
            allowed = isTl;
        } else {
            allowed = isHr;
            hrRung = true;
        }

        List<User> pool = userRepository.findByEnabledTrue().stream()
                .filter(u -> !u.getId().equals(userId))
                .filter(allowed)
                .toList();

        // On the HR rung, narrow to the real HR accounts.
        //
        // Left as a preference rather than a hard filter for the same reason as
        // the team narrowing below: if a company has nobody holding IT_HR or
        // CV_HR, an empty list is a form that cannot be submitted at all, and a
        // manager approving a leave is recoverable where that is not.
        if (hrRung) {
            List<User> realHr = pool.stream().filter(isRealHr).toList();
            if (!realHr.isEmpty()) pool = realHr;
        }

        // For a short leave, only the applicant's own team leader - that is who
        // knows the roster, and offering every team leader in the company means
        // requests land with someone who cannot judge them.
        //
        // Narrowed by team, not by department. Department was the wrong field
        // and nearly nobody has one, so this silently matched no one and the
        // applicant was shown all five team leaders. See TeamMates.
        //
        // Still widened to any team leader when their own team genuinely has
        // none: an empty list is a form that cannot be submitted at all, and
        // reaching the wrong team leader is recoverable in a way that being
        // unable to ask for leave is not.
        if (!iAmHr && !iAmTl && days <= 3 && TeamMates.hasTeam(me)) {
            List<User> myTeam = pool.stream()
                    .filter(u -> TeamMates.sameTeam(me, u))
                    .toList();
            if (!myTeam.isEmpty()) pool = myTeam;
        }

        return pool.stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());

                    // What they are to the applicant, so the dropdown can say
                    // "TL - Priya" rather than a bare name the applicant has to
                    // recognise.
                    String label;
                    if (isCto.test(u)) label = "CTO";
                    else if (isHr.test(u)) label = "HR";
                    else if (isTl.test(u)) label = "TL";
                    else label = "Approver";

                    String name = u.getName();
                    if (isCto.test(u)) {
                        // The account is stored as "CEO". The company calls the
                        // post CTO, and the person by name, so the dropdown says
                        // both rather than a title nobody uses.
                        boolean placeholder = name == null || name.isBlank()
                                || "CEO".equalsIgnoreCase(name) || "CTO".equalsIgnoreCase(name);
                        name = placeholder ? "Elamaran Subramaniyan" : name;
                    }

                    m.put("name", name);
                    m.put("role", label);
                    m.put("code", u.getEmployeeCode());
                    return m;
                }).toList();
    }

    /**
     * The approver's full leave queue across ALL statuses (for the Pending/
     * Approved/Rejected tabs). Includes rows in the approver's scope; admins see
     * everything. canAct is true only for pending rows they may decide.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> approverQueue(Long approverId) {
        Map<Long, String> typeNames = typeNameMap();
        User approver = userRepository.findById(approverId).orElse(null);
        if (approver == null) return List.of();
        boolean adminView = leaveHasRole(approver, "SUPER_ADMIN")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE");

        List<LeaveRequest> all = requestRepository.findAllByOrderByCreatedAtDesc();
        // Resolve names for applicants, chosen approvers and deciders in one go.
        java.util.Set<Long> ids = new java.util.HashSet<>();
        all.forEach(r -> {
            ids.add(r.getUserId());
            if (r.getRequestedTo() != null) ids.add(r.getRequestedTo());
            if (r.getDecidedBy() != null) ids.add(r.getDecidedBy());
        });
        Map<Long, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<LeaveRequestResponse> out = new ArrayList<>();
        for (LeaveRequest r : all) {
            User applicant = users.get(r.getUserId());
            boolean inScope = canApproveLeave(approver, applicant, r);
            boolean isTeamMember = leaveHasRole(approver, "IT_TL") && sameTeam(approver, applicant);
            boolean isHR = leaveHasRole(approver, "IT_MGR") || leaveHasRole(approver, "IT_HR");
            if (!inScope && !adminView && !isHR && !isTeamMember) continue;
            boolean canAct = inScope && "PENDING".equals(r.getStatus());
            String toName = r.getRequestedTo() != null && users.get(r.getRequestedTo()) != null
                    ? users.get(r.getRequestedTo()).getName() : null;
            String byName = r.getDecidedBy() != null && users.get(r.getDecidedBy()) != null
                    ? users.get(r.getDecidedBy()).getName() : null;
            out.add(LeaveRequestResponse.from(r,
                    applicant != null ? applicant.getName() : "?",
                    typeNames.getOrDefault(r.getLeaveTypeId(), "?"),
                    topLeaveRole(applicant), canAct, toName, byName,
                    applicant != null ? applicant.getDesignationTitle() : null,
                    applicant != null ? applicant.getEmployeeCode() : null,
                    roleLabel(users.get(r.getRequestedTo())),
                    roleLabel(users.get(r.getDecidedBy()))));
        }
        return out;
    }

    /** Everyone currently on approved leave (today falls within their from..to). */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> onLeaveToday() {
        Map<Long, String> typeNames = typeNameMap();
        List<LeaveRequest> all = requestRepository.findOnLeave(java.time.LocalDate.now());
        Map<Long, User> usersById = userRepository.findAllById(
                        all.stream().map(LeaveRequest::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        return all.stream()
                .map(r -> {
                    User u = usersById.get(r.getUserId());
                    return LeaveRequestResponse.from(r,
                            u != null ? u.getName() : "?",
                            typeNames.getOrDefault(r.getLeaveTypeId(), "?"),
                            null, false, null, null,
                            u != null ? u.getDesignationTitle() : null,
                            u != null ? u.getEmployeeCode() : null);
                })
                .toList();
    }

    /** All employees' leaves (approved + pending) overlapping a date range — for the admin calendar. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> calendar(Long viewerId, java.time.LocalDate from,
                                              java.time.LocalDate to) {
        Map<Long, String> typeNames = typeNameMap();
        List<LeaveRequest> all = requestRepository.findInRange(from, to);
        Map<Long, User> byId = userRepository.findAllById(
                        all.stream().map(LeaveRequest::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        User viewer = userRepository.findById(viewerId).orElse(null);
        // HR and admins see the whole organisation. A Team Leader sees the people
        // they lead, and themselves -- not other teams.
        boolean seesEveryone = viewer == null
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE")
                || leaveHasRole(viewer, "SUPER_ADMIN")
                || leaveHasRole(viewer, "IT_MGR")
                || leaveHasRole(viewer, "IT_HR");

        return all.stream()
                .filter(r -> {
                    if (seesEveryone) return true;
                    if (r.getUserId().equals(viewerId)) return true;
                    User applicant = byId.get(r.getUserId());
                    return applicant != null && sameTeam(viewer, applicant);
                })
                .map(r -> LeaveRequestResponse.from(r,
                        byId.containsKey(r.getUserId()) ? byId.get(r.getUserId()).getName() : "?",
                        typeNames.getOrDefault(r.getLeaveTypeId(), "?")))
                .toList();
    }

    @Transactional
    public LeaveRequestResponse decide(Long managerId, Long requestId, LeaveDecisionRequest req) {
        LeaveRequest lr = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Leave request"));
        return applyDecision(managerId, lr, req.decision(), req.comment());
    }

    @Transactional
    public int bulkDecide(Long managerId, BulkLeaveDecisionRequest req) {
        int count = 0;
        for (Long id : req.requestIds()) {
            LeaveRequest lr = requestRepository.findById(id).orElse(null);
            if (lr != null && "PENDING".equals(lr.getStatus())) {
                applyDecision(managerId, lr, req.decision(), req.comment());
                count++;
            }
        }
        return count;
    }

    private LeaveRequestResponse applyDecision(Long managerId, LeaveRequest lr,
                                               String decision, String comment) {
        if (!"PENDING".equals(lr.getStatus())) {
            throw ApiException.business("Request already " + lr.getStatus().toLowerCase());
        }
        // Enforce approval routing (TL vs Manager vs Admin by role + days).
        User approver = userRepository.findById(managerId).orElse(null);
        User applicant = userRepository.findById(lr.getUserId()).orElse(null);
        if (approver == null || !canApproveLeave(approver, applicant, lr)) {
            throw ApiException.business("You are not authorized to decide this leave request");
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (!normalized.equals("APPROVED") && !normalized.equals("REJECTED")) {
            throw ApiException.business("Decision must be APPROVED or REJECTED");
        }
        if (normalized.equals("REJECTED") && (comment == null || comment.isBlank())) {
            throw ApiException.business("A reason is required to reject a leave request");
        }

        LeaveType type = typeRepository.findById(lr.getLeaveTypeId()).orElse(null);
        if (normalized.equals("APPROVED") && type != null
                && !"LOP".equalsIgnoreCase(type.getCode())) {
            LeaveBalance balance = balanceRepository
                    .findByUserIdAndLeaveTypeIdAndYear(lr.getUserId(), lr.getLeaveTypeId(),
                            lr.getFromDate().getYear())
                    .orElseThrow(() -> ApiException.business("Balance record missing"));
            if (balance.getAvailable().compareTo(lr.getWorkingDays()) < 0) {
                throw ApiException.business("Employee no longer has sufficient balance");
            }
            balance.setUsed(balance.getUsed().add(lr.getWorkingDays()));
        }

        lr.setStatus(normalized);
        lr.setDecidedBy(managerId);
        lr.setDecidedAt(LocalDateTime.now());
        lr.setDecisionComment(comment);
        lr.setUpdatedAt(LocalDateTime.now());

        String typeName = type != null ? type.getName() : "leave";
        notificationService.createAndPush(
                lr.getUserId(),
                "Leave " + normalized.toLowerCase(),
                "Your " + typeName + " request (" + lr.getFromDate() + " to "
                        + lr.getToDate() + ") was " + normalized.toLowerCase(),
                "LEAVE",
                "/leave");

        User employee = userRepository.findById(lr.getUserId()).orElse(null);
        String empName = employee != null ? employee.getName() : "?";

        // Real-time SMS to the employee about the approval decision (fire-and-forget).
        if (employee != null && employee.getPhone() != null && !employee.getPhone().isBlank()) {
            String verb = "APPROVED".equals(normalized) ? "APPROVED" : "REJECTED";
            String sms = "Pixous HR: Hi " + empName + ", your " + typeName + " request ("
                    + lr.getFromDate() + " to " + lr.getToDate() + ") has been " + verb + "."
                    + (comment != null && !comment.isBlank() ? " Note: " + comment : "");
            smsService.send(employee.getPhone(), sms);
        }

        return LeaveRequestResponse.from(lr, empName, typeName);
    }

    private void refundBalance(LeaveRequest lr) {
        LeaveType type = typeRepository.findById(lr.getLeaveTypeId()).orElse(null);
        if (type != null && !"LOP".equalsIgnoreCase(type.getCode())) {
            balanceRepository.findByUserIdAndLeaveTypeIdAndYear(
                            lr.getUserId(), lr.getLeaveTypeId(), lr.getFromDate().getYear())
                    .ifPresent(b -> b.setUsed(
                            b.getUsed().subtract(lr.getWorkingDays()).max(BigDecimal.ZERO)));
        }
    }

    // ---------- Helpers ----------

    /**
     * Counts working days in [from, to], excluding weekends and holidays. [AC9]
     *
     * <p>This is how many days a leave request costs someone. Saturday now counts
     * as a weekend here as well, and it has to: with a Saturday deducted from a
     * leave balance but not counted as a working day by attendance or payroll,
     * somebody applying from Friday to Monday would spend three days of leave to
     * cover two days of work.
     *
     * <p>Only new requests are affected. Requests already approved keep the day
     * count stored on them; nothing is recalculated behind anybody's back.
     */
    /** "Saturday" / "Sunday", so a message can name the day rather than the rule. */
    private static String dayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
    }

    private long countWorkingDays(LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = holidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAsc(from, to).stream()
                .map(Holiday::getHolidayDate).collect(Collectors.toSet());
        long days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (com.pixous.hrportal.common.WorkCalendar.isWeekend(d)) continue;
            if (holidays.contains(d)) continue;
            days++;
        }
        return days;
    }

    private Map<Long, String> typeNameMap() {
        return typeRepository.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));
    }
}
