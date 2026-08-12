package com.pixous.hrportal.modules.payroll;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.payroll.dto.ApprovePayslipRequestDto;
import com.pixous.hrportal.modules.payroll.dto.PayslipRequestResponse;
import com.pixous.hrportal.modules.payroll.dto.PayslipResponse;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Payslip request workflow.
 *
 * <ol>
 *   <li>An employee / HR / manager raises a request for a month
 *       ({@link #raise}). It routes to Admin only (holders of PAYROLL_RUN).</li>
 *   <li>Admin sees the inbox ({@link #adminInbox}) and either rejects
 *       ({@link #reject}) or approves ({@link #approve}) by filling the
 *       customizable payslip form. Approval creates the {@link Payslip} with
 *       exactly the company/salary fields the admin entered, renders the PDF,
 *       links it back to the request, and notifies the requester.</li>
 *   <li>Only the requester can then see + download that payslip on their
 *       Payslips page (enforced by the existing owner check on the payslip
 *       download endpoint).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PayslipRequestService {

    private final PayslipRequestRepository requestRepository;
    private final PayslipRepository payslipRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;
    private final NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    /** Text a user if we have a usable mobile number for them. */
    private void sms(Long userId, String message) {
        if (userId == null) return;
        userRepository.findById(userId)
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + message));
    }

    // ---------------------------------------------------------------
    // Requester side
    // ---------------------------------------------------------------

    @Transactional
    public PayslipRequestResponse raise(Long userId, int month, int year, String note) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        // A payslip cannot exist for a month before the employee joined.
        if (user.getDateOfJoining() != null) {
            java.time.YearMonth requested = java.time.YearMonth.of(year, month);
            java.time.YearMonth joined = java.time.YearMonth.from(user.getDateOfJoining());
            if (requested.isBefore(joined)) {
                throw ApiException.business(
                        "You were not working before your joining date ("
                                + user.getDateOfJoining()
                                + "). Please try again with a month on or after "
                                + joined.getMonth() + " " + joined.getYear() + ".");
            }
        }

        // Guard against duplicate open requests for the same month.
        requestRepository
                .findByUserIdAndPayMonthAndPayYearAndStatus(userId, month, year, "PENDING")
                .ifPresent(r -> {
                    throw ApiException.business(
                            "You already have a pending payslip request for "
                                    + Month.of(month) + " " + year);
                });

        PayslipRequest r = new PayslipRequest();
        r.setUserId(userId);
        r.setPayMonth(month);
        r.setPayYear(year);
        r.setNote(note);
        r.setStatus("PENDING");
        PayslipRequest saved = requestRepository.save(r);

        // Notify every admin who can run payroll.
        String label = Month.of(month) + " " + year;
        for (User admin : userRepository.findByPermission("PAYROLL_RUN")) {
            notificationService.createAndPush(
                    admin.getId(),
                    "Payslip request",
                    user.getName() + " requested a payslip for " + label,
                    "PAYROLL",
                    "/payroll/requests");
            sms(admin.getId(), user.getName() + " requested a payslip for " + label + ".");
        }
        return PayslipRequestResponse.from(saved, user.getName(), user.getEmployeeCode());
    }

    @Transactional(readOnly = true)
    public List<PayslipRequestResponse> myRequests(Long userId) {
        String name = userRepository.findById(userId).map(User::getName).orElse("?");
        String code = userRepository.findById(userId).map(User::getEmployeeCode).orElse("?");
        return requestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> PayslipRequestResponse.from(r, name, code))
                .toList();
    }

    // ---------------------------------------------------------------
    // Admin side
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PayslipRequestResponse> adminInbox(boolean pendingOnly) {
        List<PayslipRequest> rows = pendingOnly
                ? requestRepository.findByStatusOrderByCreatedAtDesc("PENDING")
                : requestRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, User> users = userMap(rows.stream().map(PayslipRequest::getUserId).toList());
        return rows.stream().map(r -> {
            User u = users.get(r.getUserId());
            return PayslipRequestResponse.from(r,
                    u != null ? u.getName() : "?",
                    u != null ? u.getEmployeeCode() : "?");
        }).toList();
    }

    @Transactional
    public PayslipRequestResponse reject(Long adminId, Long requestId, String note) {
        PayslipRequest r = load(requestId);
        if (!"PENDING".equals(r.getStatus())) {
            throw ApiException.business("Request already " + r.getStatus().toLowerCase());
        }
        r.setStatus("REJECTED");
        r.setDecidedBy(adminId);
        r.setDecidedAt(java.time.LocalDateTime.now());
        r.setDecisionNote(note);
        r.setUpdatedAt(java.time.LocalDateTime.now());

        User u = userRepository.findById(r.getUserId()).orElse(null);
        notificationService.createAndPush(
                r.getUserId(),
                "Payslip request rejected",
                "Your payslip request for " + Month.of(r.getPayMonth()) + " "
                        + r.getPayYear() + " was rejected."
                        + (note != null && !note.isBlank() ? " Note: " + note : ""),
                "PAYROLL",
                "/payslips");
        sms(r.getUserId(), "Your payslip request for " + Month.of(r.getPayMonth()) + " "
                + r.getPayYear() + " was rejected."
                + (note != null && !note.isBlank() ? " Reason: " + note : ""));
        return PayslipRequestResponse.from(r,
                u != null ? u.getName() : "?",
                u != null ? u.getEmployeeCode() : "?");
    }

    /**
     * Approve a request by generating a payslip from the admin-entered fields.
     * The payslip is stored with all the customizable company/salary values so
     * the requester's download reproduces exactly this format.
     */
    @Transactional
    public PayslipResponse approve(Long adminId, Long requestId, ApprovePayslipRequestDto form) {
        PayslipRequest r = load(requestId);
        if (!"PENDING".equals(r.getStatus())) {
            throw ApiException.business("Request already " + r.getStatus().toLowerCase());
        }
        User user = userRepository.findById(r.getUserId())
                .orElseThrow(() -> ApiException.notFound("User"));

        // Reuse an existing payslip row for the month if present, else create.
        Payslip p = payslipRepository
                .findByUserIdAndPayMonthAndPayYear(r.getUserId(), r.getPayMonth(), r.getPayYear())
                .orElseGet(Payslip::new);
        p.setUserId(r.getUserId());
        p.setPayMonth(r.getPayMonth());
        p.setPayYear(r.getPayYear());
        p.setSource("REQUEST");

        // ---- Earnings ----
        BigDecimal basic = nz(form.basicSalary());
        BigDecimal hra = nz(form.hra());
        BigDecimal allowances = nz(form.allowances());
        BigDecimal overtime = nz(form.overtimePay());
        BigDecimal performance = nz(form.performancePay());
        BigDecimal expenses = nz(form.expensesPay());

        BigDecimal gross = basic.add(hra).add(allowances).add(overtime)
                .add(performance).add(expenses).setScale(2, RoundingMode.HALF_UP);

        // ---- Deductions (salary advance is a deduction line) ----
        BigDecimal pf = nz(form.pfDeduction());
        BigDecimal esi = nz(form.esiDeduction());
        BigDecimal pt = nz(form.ptDeduction());
        BigDecimal tds = nz(form.tdsDeduction());
        BigDecimal health = nz(form.healthInsurance());
        BigDecimal advance = nz(form.salaryAdvance());
        BigDecimal other = nz(form.otherDeductions());

        BigDecimal totalDed = pf.add(esi).add(pt).add(tds).add(health).add(advance).add(other)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(totalDed).setScale(2, RoundingMode.HALF_UP);

        p.setBasicSalary(basic);
        p.setHra(hra);
        p.setAllowances(allowances);
        p.setOvertimePay(overtime);
        p.setPerformancePay(performance);
        p.setExpensesPay(expenses);
        p.setGrossSalary(gross);
        p.setPfDeduction(pf);
        p.setEsiDeduction(esi);
        p.setPtDeduction(pt);
        p.setTdsDeduction(tds);
        p.setHealthInsurance(health);
        p.setSalaryAdvance(advance);
        p.setOtherDeductions(other);
        p.setTotalDeductions(totalDed);
        p.setNetPay(net);
        p.setLopDays(nz(form.lopDays()));

        // ---- Company / employee identity (all optional overrides) ----
        p.setCompanyName(blankToNull(form.companyName()));
        p.setCompanyLogo(blankToNull(form.companyLogo()));
        p.setCompanyGstin(blankToNull(form.companyGstin()));
        p.setCompanyAddress(blankToNull(form.companyAddress()));
        p.setBankName(blankToNull(form.bankName()));
        p.setBankAccount(blankToNull(form.bankAccount()));
        p.setDesignation(blankToNull(form.designation()));
        p.setDepartment(blankToNull(form.department()));
        p.setWorkingDays(form.workingDays());
        p.setPayDate(parseDate(form.payDate()));

        Payslip saved = payslipRepository.save(p);

        // Render the PDF using the admin-entered fields and persist its path.
        String displayName = blankToNull(form.employeeName()) != null
                ? form.employeeName() : user.getName();
        String displayCode = blankToNull(form.employeeCode()) != null
                ? form.employeeCode() : user.getEmployeeCode();
        String pdfPath = reportService.renderPayslipPdf(saved, user, displayName, displayCode);
        saved.setPdfPath(pdfPath);

        // Link + close the request.
        r.setStatus("APPROVED");
        r.setPayslipId(saved.getId());
        r.setDecidedBy(adminId);
        r.setDecidedAt(java.time.LocalDateTime.now());
        r.setDecisionNote(blankToNull(form.decisionNote()));
        r.setUpdatedAt(java.time.LocalDateTime.now());

        // Notify the requester that it's ready to download.
        notificationService.createAndPush(
                r.getUserId(),
                "Payslip ready",
                "Your payslip for " + Month.of(r.getPayMonth()) + " " + r.getPayYear()
                        + " is approved and ready to download.",
                "PAYROLL",
                "/payslips");
        sms(r.getUserId(), "Your payslip for " + Month.of(r.getPayMonth()) + " "
                + r.getPayYear() + " is approved and ready to download.");

        return PayslipResponse.from(saved, displayName, displayCode);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private PayslipRequest load(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Payslip request"));
    }

    private Map<Long, User> userMap(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Long> distinct = new ArrayList<>(ids.stream().distinct().toList());
        return userRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
