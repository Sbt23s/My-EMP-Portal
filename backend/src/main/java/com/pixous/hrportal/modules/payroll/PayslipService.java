package com.pixous.hrportal.modules.payroll;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.attendance.AttendanceRepository;
import com.pixous.hrportal.modules.org.Holiday;
import com.pixous.hrportal.modules.org.HolidayRepository;
import com.pixous.hrportal.modules.payroll.dto.*;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import com.pixous.hrportal.modules.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.pixous.hrportal.modules.org.DesignationRepository;
import com.pixous.hrportal.modules.org.DepartmentRepository;
import com.pixous.hrportal.modules.user.BankDetail;
import com.pixous.hrportal.modules.user.BankDetailRepository;

/**
 * Payslip generation. Mirrors the legacy PHP contract
 * (payslip/index.php action=generate|list) with components
 * basic_salary / hra / allowances / deductions, extended with
 * statutory PF / ESI / PT and attendance-driven LOP.
 */
@Service
@RequiredArgsConstructor
public class PayslipService {

    private static final BigDecimal ESI_WAGE_CEILING = new BigDecimal("21000");
    private static final BigDecimal ESI_RATE = new BigDecimal("0.0075"); // 0.75% employee share
    private static final BigDecimal OT_HOURLY_DIVISOR = new BigDecimal("240"); // ~30 days * 8h

    private final SalaryStructureRepository salaryRepository;
    private final SalaryMonthRepository salaryMonthRepository;
    private final PayslipRepository payslipRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final HolidayRepository holidayRepository;
    private final ReportService reportService;
    private final com.pixous.hrportal.common.MailService mailService;
    private final PayrollRunRepository payrollRunRepository;
    private final NotificationService notificationService;
    private final BankDetailRepository bankDetailRepository;
    private final DesignationRepository designationRepository;
    private final com.pixous.hrportal.modules.audit.AuditService auditService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final DepartmentRepository departmentRepository;

    @Transactional
    public PayslipResponse generate(GeneratePayslipRequest req) {
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> ApiException.notFound("User"));

        // Cannot generate a payslip for a month before the employee joined.
        if (user.getDateOfJoining() != null) {
            java.time.YearMonth requested = java.time.YearMonth.of(req.year(), req.month());
            java.time.YearMonth joined = java.time.YearMonth.from(user.getDateOfJoining());
            if (requested.isBefore(joined)) {
                throw ApiException.business(
                        user.getName() + " was not working before the joining date ("
                                + user.getDateOfJoining() + "). Choose a month on or after "
                                + joined.getMonth() + " " + joined.getYear() + ".");
            }
        }

        /*
         * The structure that applied to the month being paid, not the one
         * that applies today.
         *
         * Reading the active structure meant a raise in October changed the
         * September payslip the moment it was regenerated -- a historical
         * payslip moving when nothing about that month moved. The month's
         * last day is the date asked about, so a structure that took effect
         * partway through the month still governs it.
         */
        java.time.LocalDate asOf = YearMonth.of(req.year(), req.month()).atEndOfMonth();
        SalaryStructure salary = salaryRepository.findEffectiveOn(req.userId(), asOf)
                .stream().findFirst()
                // Falling back to the active structure keeps an employee
                // whose only row starts after this month payable, rather than
                // failing the run over a date that was filled in later.
                .or(() -> salaryRepository.findByUserIdAndActiveTrue(req.userId()))
                .orElseThrow(() -> ApiException.business(
                        "No active salary structure for " + user.getName()));

        /*
         * A payslip that already exists for this month is being regenerated,
         * not created. The figures are about to be overwritten in place, so
         * the revision counts up: without it a number somebody was shown last
         * week could change with nothing on the record saying it had.
         */
        Payslip existing = payslipRepository
                .findByUserIdAndPayMonthAndPayYear(req.userId(), req.month(), req.year())
                .orElse(null);
        boolean regenerating = existing != null;
        Payslip p = existing != null ? existing : new Payslip();
        if (regenerating) {
            p.setRevision((p.getRevision() == null ? 1 : p.getRevision()) + 1);
        }
        p.setUserId(req.userId());
        p.setPayMonth(req.month());
        p.setPayYear(req.year());

        // ---- Earnings ----
        // The basic recorded against this month wins, so a figure entered under
        // Salary details is what the payslip is built on. Employees with no month
        // row fall back to their standing structure exactly as before.
        BigDecimal basic = salaryMonthRepository
                .findByUserIdAndPayYearAndPayMonth(req.userId(), req.year(), req.month())
                .map(SalaryMonth::getBasicSalary)
                .orElse(salary.getBasicSalary());
        BigDecimal hra = salary.getHra();
        BigDecimal allowances = salary.getAllowances();

        /*
         * A day's pay is a working day's pay, not a calendar day's.
         *
         * Dividing by 30 makes an absence cost less than the day was worth --
         * somebody paid 20,000 for 26 working days earns 769 a day, and
         * deducting 667 for missing one leaves the company paying for time
         * nobody worked. Weekends and public holidays are already excluded
         * from the count, so this is the figure both sides would recognise.
         */
        AttendanceMonth att = countMonth(req.userId(), req.month(), req.year());
        int workingDays = Math.max(1, att.workingDays());
        BigDecimal perDayGross = basic.add(hra).add(allowances)
                .divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);

        // Overtime pay = hourly rate * OT hours
        BigDecimal otHours = BigDecimal.valueOf(
                req.overtimeHours() != null ? req.overtimeHours() : 0.0);
        BigDecimal hourlyRate = basic.add(hra).add(allowances)
                .divide(OT_HOURLY_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal overtimePay = hourlyRate.multiply(otHours).setScale(2, RoundingMode.HALF_UP);

        /*
         * Absences come from attendance rather than from a box somebody fills
         * in. The figure was being typed in every month, which meant payroll
         * agreed with the attendance register only when somebody copied it
         * across correctly.
         *
         * A manual amount still exists and is added on top: it is how a
         * one-off correction is made without editing the attendance history.
         * The two do not double-count because they measure different things --
         * this one counts days, that one is a rupee figure.
         */
        BigDecimal lopDays = BigDecimal.valueOf(att.unpaidDays());

        // Performance pay — an extra earning entered by the admin.
        BigDecimal performance = amt(req.performancePay());

        BigDecimal gross = basic.add(hra).add(allowances).add(overtimePay).add(performance)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // ---- Deductions ----
        // PF is a flat rupee amount entered by the admin (not a percentage).
        BigDecimal pf = salary.getPfPercentage().setScale(2, RoundingMode.HALF_UP);

        BigDecimal esi = BigDecimal.ZERO;
        if (salary.isEsiApplicable() && gross.compareTo(ESI_WAGE_CEILING) <= 0) {
            esi = gross.multiply(ESI_RATE).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal pt = salary.getPtAmount();
        BigDecimal tds = amt(req.tds());
        BigDecimal advance = amt(req.advanceDeduction());
        // What the absent days cost, at a working day's rate.
        BigDecimal absentDeduction = perDayGross.multiply(lopDays)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal lop = amt(req.lopDeduction()).add(absentDeduction)
                .setScale(2, RoundingMode.HALF_UP);
        // Manual loss-of-pay is grouped with any other deductions for storage.
        BigDecimal otherDed = amt(req.otherDeductions()).add(lop).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalDed = pf.add(esi).add(pt).add(tds).add(otherDed).add(advance)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(totalDed).setScale(2, RoundingMode.HALF_UP);

        p.setBasicSalary(basic);
        p.setHra(hra);
        p.setAllowances(allowances);
        p.setOvertimePay(overtimePay);
        p.setPerformancePay(performance);
        p.setGrossSalary(gross);
        p.setPfDeduction(pf);
        p.setEsiDeduction(esi);
        p.setPtDeduction(pt);
        p.setTdsDeduction(tds);
        p.setOtherDeductions(otherDed);
        p.setSalaryAdvance(advance);
        p.setTotalDeductions(totalDed);
        p.setNetPay(net);

        // ---- Auto-fill employee profile details ----
        // 1. Bank details
        List<BankDetail> banks = bankDetailRepository.findByUserId(req.userId());
        if (!banks.isEmpty()) {
            BankDetail bank = banks.stream()
                    .filter(BankDetail::isPrimary)
                    .findFirst()
                    .orElse(banks.get(0));
            p.setBankName(bank.getBankName());
            p.setBankAccount(bank.getAccountNumber());
        } else {
            p.setBankName("-");
            p.setBankAccount("-");
        }

        // 2. Designation
        String designation = user.getDesignationTitle();
        if ((designation == null || designation.isBlank()) && user.getDesignationId() != null) {
            designation = designationRepository.findById(user.getDesignationId())
                    .map(com.pixous.hrportal.modules.org.Designation::getName)
                    .orElse("-");
        }
        p.setDesignation(designation != null && !designation.isBlank() ? designation : "-");

        // 3. Department
        String department = user.getDepartmentTitle();
        if ((department == null || department.isBlank()) && user.getDepartmentId() != null) {
            department = departmentRepository.findById(user.getDepartmentId())
                    .map(com.pixous.hrportal.modules.org.Department::getName)
                    .orElse("-");
        }
        p.setDepartment(department != null && !department.isBlank() ? department : "-");

        // 4. Pay Date & Working Days
        p.setPayDate(LocalDate.now());
        // Working days, not calendar days: this is what the per-day rate is
        // derived from, so showing 30 beside a rate built on 26 would make the
        // payslip fail its own arithmetic.
        p.setWorkingDays(att.workingDays());

        // 5. Attendance-driven LOP days count
        // The same count the deduction was built from, so the days shown and
        // the money taken cannot disagree.
        BigDecimal lopDaysVal = lopDays;
        p.setLopDays(lopDaysVal);

        Payslip saved = payslipRepository.save(p);

        // Render PDF and persist its path
        String pdfPath = reportService.renderPayslipPdf(saved, user);
        saved.setPdfPath(pdfPath);

        /*
         * Salary is money and the figures can be regenerated, so who did it
         * and when is worth keeping. Recorded after the PDF exists, so a run
         * that failed halfway does not leave a log line claiming otherwise.
         */
        auditService.record(
                com.pixous.hrportal.security.SecurityUtils.currentUserId(),
                "PAYROLL",
                regenerating ? "PAYSLIP_REGENERATED" : "PAYSLIP_GENERATED",
                (regenerating ? "Regenerated" : "Generated") + " the "
                        + java.time.Month.of(req.month()) + " " + req.year()
                        + " payslip for " + user.getName()
                        + " (net " + saved.getNetPay() + ", revision " + saved.getRevision() + ")",
                "PAYSLIP", saved.getId(),
                String.format("PS-%d-%02d-%s", req.year(), req.month(),
                        user.getEmployeeCode() == null ? user.getId() : user.getEmployeeCode()));

        return PayslipResponse.from(saved, user.getName(), user.getEmployeeCode());
    }

    @Transactional(readOnly = true)
    public List<PayslipSummary> list(Long userId) {
        return payslipRepository.findByUserIdOrderByPayYearDescPayMonthDesc(userId).stream()
                .map(PayslipSummary::from).toList();
    }

    /** userId -> payslip summary for a given month (for the admin month view). */
    @Transactional(readOnly = true)
    public java.util.Map<Long, PayslipSummary> listByMonth(int month, int year) {
        return payslipRepository.findByPayMonthAndPayYear(month, year).stream()
                .collect(Collectors.toMap(Payslip::getUserId, PayslipSummary::from, (a, b) -> a));
    }

    // ---- Salary structure (admin-entered pay for an employee) ----

    @Transactional
    public SalaryStructureResponse upsertSalary(SalaryStructureRequest req) {
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> ApiException.notFound("User"));
        SalaryStructure s = salaryRepository.findByUserIdAndActiveTrue(req.userId())
                .orElseGet(SalaryStructure::new);
        s.setUserId(req.userId());
        s.setBasicSalary(req.basicSalary());
        s.setHra(req.hra() != null ? req.hra() : BigDecimal.ZERO);
        s.setAllowances(req.allowances() != null ? req.allowances() : BigDecimal.ZERO);
        s.setPfPercentage(req.pfPercentage() != null ? req.pfPercentage() : BigDecimal.ZERO);
        s.setEsiApplicable(req.esiApplicable() == null || req.esiApplicable());
        s.setPtAmount(req.ptAmount() != null ? req.ptAmount() : BigDecimal.ZERO);
        s.setActive(true);
        if (s.getEffectiveFrom() == null) s.setEffectiveFrom(LocalDate.now());
        return toSalaryResponse(salaryRepository.save(s), user);
    }

    @Transactional(readOnly = true)
    public SalaryStructureResponse getSalary(Long userId) {
        return salaryRepository.findByUserIdAndActiveTrue(userId)
                .map(s -> toSalaryResponse(s, userRepository.findById(userId).orElse(null)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SalaryStructureResponse> listSalaries() {
        List<SalaryStructure> all = salaryRepository.findByActiveTrue();
        java.util.Map<Long, User> users = userRepository.findAllById(
                        all.stream().map(SalaryStructure::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        return all.stream().map(s -> toSalaryResponse(s, users.get(s.getUserId()))).toList();
    }

    // ---- Basic salary, month by month ----

    /** Every basic recorded for one month, for the Salary details table. */
    @Transactional(readOnly = true)
    public List<SalaryMonthResponse> listSalaryMonths(int month, int year) {
        return salaryMonthRepository.findByPayYearAndPayMonth(year, month).stream()
                .map(m -> new SalaryMonthResponse(m.getUserId(), m.getPayMonth(), m.getPayYear(),
                        m.getBasicSalary()))
                .toList();
    }

    /** One employee's own basic pay across every month recorded, newest first. */
    @Transactional(readOnly = true)
    public List<SalaryMonthResponse> salaryMonthsForUser(Long userId) {
        return salaryMonthRepository.findByUserIdOrderByPayYearDescPayMonthDesc(userId).stream()
                .map(m -> new SalaryMonthResponse(m.getUserId(), m.getPayMonth(), m.getPayYear(),
                        m.getBasicSalary()))
                .toList();
    }

    /** Records (or replaces) one employee's basic pay for one month. */
    @Transactional
    public SalaryMonthResponse upsertSalaryMonth(SalaryMonthRequest req) {
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> ApiException.notFound("User"));

        // A month before somebody joined is not a month they were paid for.
        if (user.getDateOfJoining() != null) {
            YearMonth asked = YearMonth.of(req.year(), req.month());
            YearMonth joined = YearMonth.from(user.getDateOfJoining());
            if (asked.isBefore(joined)) {
                throw ApiException.business(user.getName() + " joined in "
                        + joined.getMonth() + " " + joined.getYear()
                        + ", so there is no basic pay to record for an earlier month.");
            }
        }

        SalaryMonth m = salaryMonthRepository
                .findByUserIdAndPayYearAndPayMonth(req.userId(), req.year(), req.month())
                .orElseGet(SalaryMonth::new);
        m.setUserId(req.userId());
        m.setPayYear(req.year());
        m.setPayMonth(req.month());
        m.setBasicSalary(req.basicSalary());
        SalaryMonth saved = salaryMonthRepository.save(m);
        return new SalaryMonthResponse(saved.getUserId(), saved.getPayMonth(), saved.getPayYear(),
                saved.getBasicSalary());
    }

    private SalaryStructureResponse toSalaryResponse(SalaryStructure s, User user) {
        BigDecimal gross = s.getBasicSalary().add(s.getHra()).add(s.getAllowances());
        return new SalaryStructureResponse(
                s.getUserId(),
                user != null ? user.getName() : null,
                user != null ? user.getEmployeeCode() : null,
                s.getBasicSalary(), s.getHra(), s.getAllowances(),
                s.getPfPercentage(), s.isEsiApplicable(), s.getPtAmount(), gross);
    }

    private static BigDecimal amt(Double v) {
        return BigDecimal.valueOf(v != null ? v : 0.0).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public PayslipResponse get(Long requesterId, Long payslipId, boolean privileged) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> ApiException.notFound("Payslip"));
        if (!privileged && !p.getUserId().equals(requesterId)) {
            throw ApiException.business("You can only view your own payslips");
        }
        User u = userRepository.findById(p.getUserId()).orElse(null);
        return PayslipResponse.from(p,
                u != null ? u.getName() : "?",
                u != null ? u.getEmployeeCode() : "?");
    }

    @Transactional
    public PayrollRunResponse generateBatch(int month, int year, Long runBy) {
        var priorRun = payrollRunRepository.findByPayMonthAndPayYear(month, year);
        if (priorRun.isPresent() && "FINALIZED".equals(priorRun.get().getStatus())) {
            throw ApiException.business(
                    "That month is finalised. The figures are what was paid, so they cannot be regenerated.");
        }
        if (priorRun.isPresent()) {
            throw ApiException.business("Payroll run for this month already exists");
        }
        
        PayrollRun run = new PayrollRun();
        run.setPayMonth(month);
        run.setPayYear(year);
        run.setRunBy(runBy);
        run.setRunAt(java.time.LocalDateTime.now());
        run.setStatus("PREVIEW");
        PayrollRun savedRun = payrollRunRepository.save(run);

        List<User> activeUsers = userRepository.findByEnabledTrue();
        int total = activeUsers.size();
        int done = 0;
        int failed = 0;
        java.util.List<java.util.Map<String, Object>> failures = new java.util.ArrayList<>();

        publishProgress(savedRun.getId(), 0, total, 0, null);

        for (User u : activeUsers) {
            try {
                GeneratePayslipRequest req = new GeneratePayslipRequest(u.getId(), month, year, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                PayslipResponse resp = generate(req);
                Payslip p = payslipRepository.findById(resp.id()).orElseThrow();
                p.setPayrollRunId(savedRun.getId());
                payslipRepository.save(p);
                done++;
            } catch (Exception e) {
                /*
                 * One employee failing must not stop the other thirty. The
                 * usual cause is no active salary structure, which is a thing
                 * to go and fix rather than a reason to abandon the run -- so
                 * the name and the reason are collected and reported at the
                 * end instead of being swallowed.
                 */
                failed++;
                failures.add(java.util.Map.of(
                        "userId", u.getId(),
                        "name", u.getName() == null ? "" : u.getName(),
                        "employeeCode", u.getEmployeeCode() == null ? "" : u.getEmployeeCode(),
                        "reason", e.getMessage() == null ? "Could not be calculated" : e.getMessage()));
            }
            publishProgress(savedRun.getId(), done + failed, total, failed, u.getName());
        }

        auditService.record(runBy, "PAYROLL", "PAYROLL_RUN",
                "Ran " + java.time.Month.of(month) + " " + year + " payroll: "
                        + done + " generated, " + failed + " failed",
                "PAYROLL_RUN", savedRun.getId(),
                java.time.Month.of(month) + " " + year);

        publishDone(savedRun.getId(), done, failed, total, failures);
        return getRun(savedRun.getId());
    }

    /**
     * Say where the run has got to, so the page can count up without asking.
     *
     * <p>Broadcast rather than sent to one person: a payroll run is watched by
     * whoever started it and often by somebody else at the same time, and the
     * numbers are not private -- they are counts, not salaries.
     *
     * <p>Nothing here throws. A progress bar that fails must not take the
     * payroll down with it.
     */
    private void publishProgress(Long runId, int done, int total, int failed, String current) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("runId", runId);
            body.put("done", done);
            body.put("total", total);
            body.put("failed", failed);
            body.put("current", current == null ? "" : current);
            body.put("finished", false);
            messagingTemplate.convertAndSend("/topic/payroll", body);
        } catch (Exception ignored) {
            // See above: progress is a courtesy beside the work.
        }
    }

    /** The final tally, including who could not be calculated and why. */
    private void publishDone(Long runId, int done, int failed, int total,
                             java.util.List<java.util.Map<String, Object>> failures) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("runId", runId);
            body.put("done", done);
            body.put("total", total);
            body.put("failed", failed);
            body.put("failures", failures);
            body.put("finished", true);
            messagingTemplate.convertAndSend("/topic/payroll", body);
        } catch (Exception ignored) {
            // See above.
        }
    }

    @Transactional
    public PayrollRunResponse confirmRun(Long runId, Long runBy) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Payroll run"));
        if (!"PREVIEW".equals(run.getStatus())) {
            throw ApiException.business("Run is not in PREVIEW state");
        }
        run.setStatus("CONFIRMED");
        payrollRunRepository.save(run);
        return getRun(runId);
    }

    /**
     * Close a payroll run for good.
     *
     * <p>The last state. Up to here a run can be regenerated -- attendance
     * gets corrected, a salary structure is filled in, and the figures should
     * follow. Once it is finalised the month is paid and the numbers are what
     * was paid, so regenerating would rewrite history rather than correct it.
     *
     * <p>Reopening is deliberately not offered here. A month that has to
     * change after payment is an exception that should involve somebody
     * deciding, not a button.
     */
    @Transactional
    public PayrollRunResponse finaliseRun(Long runId, Long actorId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Payroll run"));
        if ("FINALIZED".equals(run.getStatus())) {
            throw ApiException.business("This run is already finalised.");
        }
        if (!"FINANCE_APPROVED".equals(run.getStatus())) {
            throw ApiException.business(
                    "A run is finalised after finance has approved it. This one is "
                            + run.getStatus().toLowerCase().replace('_', ' ') + ".");
        }
        run.setStatus("FINALIZED");
        payrollRunRepository.save(run);

        auditService.record(actorId, "PAYROLL", "PAYROLL_FINALIZED",
                "Finalised the " + java.time.Month.of(run.getPayMonth()) + " "
                        + run.getPayYear() + " payroll",
                "PAYROLL_RUN", run.getId(),
                java.time.Month.of(run.getPayMonth()) + " " + run.getPayYear());

        return getRun(runId);
    }

    @Transactional
    public PayrollRunResponse financeApproveRun(Long runId, Long approvedBy) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Payroll run"));
        if (!"CONFIRMED".equals(run.getStatus())) {
            throw ApiException.business("Run is not in CONFIRMED state");
        }
        run.setStatus("FINANCE_APPROVED");
        run.setFinanceApprovedBy(approvedBy);
        run.setFinanceApprovedAt(java.time.LocalDateTime.now());
        payrollRunRepository.save(run);

        // Notify all employees
        List<Payslip> slips = payslipRepository.findByPayrollRunId(runId);
        for (Payslip p : slips) {
            notificationService.createAndPush(
                    p.getUserId(),
                    "Payslip Available",
                    "Your payslip for " + java.time.Month.of(run.getPayMonth()) + " " + run.getPayYear() + " is ready.",
                    "PAYROLL",
                    "/payslips"
            );
        }
        return getRun(runId);
    }

    @Transactional(readOnly = true)
    public List<PayrollRunSummary> listRuns() {
        return payrollRunRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getPayYear() != b.getPayYear()) return b.getPayYear() - a.getPayYear();
                    return b.getPayMonth() - a.getPayMonth();
                })
                .map(PayrollRunSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public PayrollRunResponse getRun(Long runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Payroll run"));
        List<PayslipResponse> slips = payslipRepository.findByPayrollRunId(runId).stream()
                .map(p -> {
                    User u = userRepository.findById(p.getUserId()).orElse(null);
                    return PayslipResponse.from(p, u != null ? u.getName() : "?", u != null ? u.getEmployeeCode() : "?");
                }).toList();
        return PayrollRunResponse.from(run, slips);
    }

    @Transactional(readOnly = true)
    public byte[] pdfBytes(Long requesterId, Long payslipId, boolean privileged) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> ApiException.notFound("Payslip"));
        if (!privileged && !p.getUserId().equals(requesterId)) {
            throw ApiException.business("You can only download your own payslips");
        }
        User u = userRepository.findById(p.getUserId())
                .orElseThrow(() -> ApiException.notFound("User"));
        // Always regenerate payslip on-the-fly to guarantee updated designation/bank metadata is shown
        return reportService.payslipPdfBytes(p, u);
    }

    /**
     * Email one payslip to the person it belongs to.
     *
     * <p>The address is read from their profile rather than typed by whoever
     * presses the button, so a payslip cannot reach the wrong person through a
     * slip of the keyboard. Personal email is preferred over the work address:
     * somebody who has left, or is about to, still needs their payslip, and a
     * work mailbox is not private to them.
     *
     * <p>The PDF is generated fresh here rather than read from storage, exactly
     * as the download does, so the copy that arrives by mail and the copy taken
     * from the portal are always the same document.
     *
     * @return the address it was sent to, so the caller can say where it went
     */
    @Transactional(readOnly = true)
    public String emailToEmployee(Long requesterId, Long payslipId, boolean privileged) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> ApiException.notFound("Payslip"));
        /*
          An employee may send their own payslip and nobody else's.
          
          There is little to abuse here even so -- the destination is read from
          the profile below, so the worst anyone can do is mail their own
          payslip to their own address. The check exists because "send" should
          obey the same rule as "download", not because the alternative would
          be catastrophic.
        */
        if (!privileged && !p.getUserId().equals(requesterId)) {
            throw ApiException.business("You can only email your own payslips");
        }
        User u = userRepository.findById(p.getUserId())
                .orElseThrow(() -> ApiException.notFound("User"));

        /*
          Work address first, personal only as a fallback.

          It was the other way round, on the reasoning that a payslip is
          personal and a work mailbox is not private. That is true in general
          and wrong here: the work address is the one the company issued, the
          one HR knows, and the one the employee is expecting company post to
          arrive at. Sending to a personal address they may not have checked
          in months -- or may not remember giving -- looks like the payslip
          never arrived at all, which is exactly what happened.

          Personal remains the fallback for anyone with no work address on
          their record, so nobody is left unable to receive their payslip.
        */
        String to = firstNonBlank(u.getEmail(), u.getPersonalEmail());
        if (to == null) {
            throw ApiException.business(
                    u.getName() + " has no email address on their profile, so there is "
                    + "nowhere to send it. Add one on their employee record first.");
        }

        String period = monthName(p.getPayMonth()) + " " + p.getPayYear();
        String subject = "Payslip for " + period + " - Pixous Technologies";

        String body =
                "<p>Dear " + escapeHtml(u.getName()) + ",</p>"
                + "<p>Your payslip for <strong>" + escapeHtml(period) + "</strong> is attached.</p>"
                + "<p>If anything on it looks wrong, reply to this email or speak to HR.</p>"
                + "<p>Pixous Technologies</p>"
                + "<p style=\"color:#6b7280;font-size:12px\">"
                + "This message was sent automatically by the HR portal. "
                + "The attachment is confidential and intended only for you.</p>";

        String fileName = "Payslip-" + period.replace(' ', '-') + ".pdf";
        mailService.sendWithPdf(to, subject, body, fileName, reportService.payslipPdfBytes(p, u));
        return to;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    /** Names are user-supplied and go into HTML, so they are escaped. */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String monthName(int month) {
        if (month < 1 || month > 12) return String.valueOf(month);
        return java.time.Month.of(month)
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
    }

    // ---- helpers ----

    private int daysInMonth(int month, int year) {
        return YearMonth.of(year, month).lengthOfMonth();
    }

    /** What one employee's month looked like, as payroll needs to see it. */
    public record AttendanceMonth(
            int calendarDays, int workingDays, int presentDays,
            int paidDays, int unpaidDays, int holidays, int wfhDays) {
    }

    /**
     * Count a month from the attendance register.
     *
     * <p>Separate from computeLopDays below, which asks a narrower question:
     * it counts a day as worked only if somebody punched in, so an approved
     * work-from-home day or a day of paid leave reads as an absence. Fine for
     * the report it feeds; wrong for pay.
     *
     * <p>Here a day is paid if it was worked, worked from home, or covered by
     * leave that carries pay. Only a working day with none of those is
     * deducted. Future days in the current month are skipped, so a run on the
     * 10th does not treat the rest of the month as absence; working days are
     * counted over the whole month regardless, so a day's pay does not change
     * depending on when payroll is run.
     */
    private AttendanceMonth countMonth(Long userId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        LocalDate end = monthEnd;
        LocalDate today = LocalDate.now();
        if (end.isAfter(today)) end = today;

        Set<LocalDate> holidays = holidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAsc(start, monthEnd).stream()
                .map(Holiday::getHolidayDate).collect(Collectors.toSet());

        int workingDays = 0;
        int holidayCount = 0;
        for (LocalDate d = start; !d.isAfter(monthEnd); d = d.plusDays(1)) {
            if (com.pixous.hrportal.common.WorkCalendar.isWeekend(d)) continue;
            if (holidays.contains(d)) { holidayCount++; continue; }
            workingDays++;
        }

        java.util.Map<LocalDate, String> byDay = new java.util.HashMap<>();
        if (!end.isBefore(start)) {
            attendanceRepository
                    .findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(userId, start, end)
                    .forEach(a -> byDay.put(a.getWorkDate(),
                            a.getStatus() == null ? "" : a.getStatus().toUpperCase()));
        }

        int present = 0, paid = 0, unpaid = 0, wfh = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (com.pixous.hrportal.common.WorkCalendar.isWeekend(d)) continue;
            if (holidays.contains(d)) continue;
            String status = byDay.get(d);
            if (status == null) { unpaid++; continue; }
            switch (status) {
                case "WFH" -> { present++; wfh++; }
                case "PRESENT", "LATE", "HALF_DAY" -> present++;
                case "LEAVE", "PAID_LEAVE", "ON_LEAVE" -> paid++;
                default -> unpaid++;
            }
        }

        return new AttendanceMonth(ym.lengthOfMonth(), workingDays, present,
                paid, unpaid, holidayCount, wfh);
    }

    /** Working days in the month with neither attendance nor approved leave. */
    private long computeLopDays(Long userId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        // do not count future days within the current month
        LocalDate today = LocalDate.now();
        if (end.isAfter(today)) end = today;
        if (end.isBefore(start)) return 0;

        Set<LocalDate> holidays = holidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAsc(start, end).stream()
                .map(Holiday::getHolidayDate).collect(Collectors.toSet());

        Set<LocalDate> presentDays = attendanceRepository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(userId, start, end).stream()
                .filter(a -> a.getPunchInAt() != null)
                .map(a -> a.getWorkDate()).collect(Collectors.toSet());

        long lop = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            // Saturday is a weekend. Counted as a working day, every Saturday
            // nobody punched became a day of Loss of Pay — real money deducted
            // for a day nobody was asked to work.
            if (com.pixous.hrportal.common.WorkCalendar.isWeekend(d)) continue;
            if (holidays.contains(d)) continue;
            if (presentDays.contains(d)) continue;
            lop++;
        }
        return lop;
    }
}
