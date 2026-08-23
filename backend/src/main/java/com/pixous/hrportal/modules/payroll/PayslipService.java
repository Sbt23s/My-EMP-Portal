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

        SalaryStructure salary = salaryRepository.findByUserIdAndActiveTrue(req.userId())
                .orElseThrow(() -> ApiException.business(
                        "No active salary structure for " + user.getName()));

        Payslip p = payslipRepository
                .findByUserIdAndPayMonthAndPayYear(req.userId(), req.month(), req.year())
                .orElseGet(Payslip::new);
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

        BigDecimal perDayGross = basic.add(hra).add(allowances)
                .divide(BigDecimal.valueOf(daysInMonth(req.month(), req.year())), 2, RoundingMode.HALF_UP);

        // Overtime pay = hourly rate * OT hours
        BigDecimal otHours = BigDecimal.valueOf(
                req.overtimeHours() != null ? req.overtimeHours() : 0.0);
        BigDecimal hourlyRate = basic.add(hra).add(allowances)
                .divide(OT_HOURLY_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal overtimePay = hourlyRate.multiply(otHours).setScale(2, RoundingMode.HALF_UP);

        // Loss of Pay is now entered manually by the admin (attendance-driven
        // auto-LOP is disabled so the two can't double-count).
        BigDecimal lopDays = BigDecimal.ZERO;

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
        BigDecimal lop = amt(req.lopDeduction());
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
        p.setWorkingDays(daysInMonth(req.month(), req.year()));

        // 5. Attendance-driven LOP days count
        BigDecimal lopDaysVal = BigDecimal.valueOf(computeLopDays(req.userId(), req.month(), req.year()));
        p.setLopDays(lopDaysVal);

        Payslip saved = payslipRepository.save(p);

        // Render PDF and persist its path
        String pdfPath = reportService.renderPayslipPdf(saved, user);
        saved.setPdfPath(pdfPath);

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
        if (payrollRunRepository.findByPayMonthAndPayYear(month, year).isPresent()) {
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
        for (User u : activeUsers) {
            try {
                GeneratePayslipRequest req = new GeneratePayslipRequest(u.getId(), month, year, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                PayslipResponse resp = generate(req);
                Payslip p = payslipRepository.findById(resp.id()).orElseThrow();
                p.setPayrollRunId(savedRun.getId());
                payslipRepository.save(p);
            } catch (Exception e) {
                // skip users without active salary structures etc.
            }
        }
        return getRun(savedRun.getId());
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
    public String emailToEmployee(Long payslipId) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> ApiException.notFound("Payslip"));
        User u = userRepository.findById(p.getUserId())
                .orElseThrow(() -> ApiException.notFound("User"));

        String to = firstNonBlank(u.getPersonalEmail(), u.getEmail());
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
