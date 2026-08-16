package com.pixous.hrportal.modules.payroll;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.ErrorCode;
import com.pixous.hrportal.modules.payroll.dto.*;
import com.pixous.hrportal.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayslipService service;
    private final PayslipRequestService requestService;
    private final com.pixous.hrportal.common.StorageService storageService;

    /** Generate (or regenerate) a payslip — Finance/HR only. Mirrors payslip action=generate. */
    @PostMapping("/payslip/generate")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<PayslipResponse> generate(@Valid @RequestBody GeneratePayslipRequest req) {
        return ApiResponse.ok(service.generate(req), "Payslip generated");
    }

    // ---- Salary structure (admin sets each employee's pay) ----

    @PostMapping("/salary")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<SalaryStructureResponse> setSalary(@Valid @RequestBody SalaryStructureRequest req) {
        return ApiResponse.ok(service.upsertSalary(req), "Salary saved");
    }

    /**
     * Who is allowed to look at someone else's pay.
     *
     * PAYROLL_VIEW is seeded as "View own payslips" and every employee has it, so
     * it can only ever mean "may see my own". Guarding these two endpoints with it
     * meant any employee could read the whole company's salaries, and — because
     * IT_HR is not granted it — that HR could read none. Both halves of that come
     * from the same mistake.
     *
     * Running payroll (PAYROLL_RUN), approving it (PAYROLL_APPROVE) or managing
     * people (USER_MANAGE / EMPLOYEE_MANAGE) is what makes someone else's salary
     * your business.
     */
    private boolean maySeeEveryonesPay() {
        return SecurityUtils.hasAuthority("PAYROLL_RUN")
                || SecurityUtils.hasAuthority("PAYROLL_APPROVE")
                || SecurityUtils.hasAuthority("USER_MANAGE")
                || SecurityUtils.hasAuthority("EMPLOYEE_MANAGE");
    }

    @GetMapping("/salary/{userId}")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ApiResponse<SalaryStructureResponse> getSalary(@PathVariable Long userId) {
        // Asking for an id that is not yours is refused unless you are one of the
        // people above. Previously any employee could read any other employee's
        // salary simply by putting their id in the URL.
        Long me = SecurityUtils.currentUserId();
        if (!maySeeEveryonesPay() && !userId.equals(me)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "You can only view your own salary");
        }
        return ApiResponse.ok(service.getSalary(userId));
    }

    @GetMapping("/salaries")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ApiResponse<List<SalaryStructureResponse>> listSalaries() {
        // Same endpoint, same callers, same shape of answer -- an employee simply
        // gets a list containing their own row and nothing else, instead of
        // everyone's. Nothing on the client needs to change.
        if (maySeeEveryonesPay()) {
            return ApiResponse.ok(service.listSalaries());
        }
        SalaryStructureResponse mine = service.getSalary(SecurityUtils.currentUserId());
        return ApiResponse.ok(mine == null ? List.of() : List.of(mine));
    }

    // ---- Basic salary month by month (Salary details) ----

    /** Basic salary recorded for every employee in one month. */
    @GetMapping("/salary-months")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ApiResponse<List<SalaryMonthResponse>> salaryMonths(
            @RequestParam int month, @RequestParam int year) {
        return ApiResponse.ok(service.listSalaryMonths(month, year));
    }

    /** Record one employee's basic salary for one month. */
    @PostMapping("/salary-months")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<SalaryMonthResponse> setSalaryMonth(@Valid @RequestBody SalaryMonthRequest req) {
        return ApiResponse.ok(service.upsertSalaryMonth(req), "Basic salary saved");
    }

    /** The signed-in employee's own basic salary, month by month. Self-service. */
    @GetMapping("/salary-months/me")
    public ApiResponse<List<SalaryMonthResponse>> mySalaryMonths() {
        return ApiResponse.ok(service.salaryMonthsForUser(SecurityUtils.currentUserId()));
    }

    /** All employees' payslips for a month, keyed by userId (admin month view). */
    @GetMapping("/payslips/month")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ApiResponse<Map<Long, PayslipSummary>> payslipsByMonth(
            @RequestParam int month, @RequestParam int year) {
        return ApiResponse.ok(service.listByMonth(month, year));
    }

    /** List the caller's own payslips. Mirrors payslip action=list. */
    @GetMapping("/payslip/list")
    public ApiResponse<List<PayslipSummary>> myPayslips() {
        return ApiResponse.ok(service.list(SecurityUtils.currentUserId()));
    }

    /** List a specific employee's payslips — privileged. */
    @GetMapping("/payslip/list/{userId}")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ApiResponse<List<PayslipSummary>> payslipsFor(@PathVariable Long userId) {
        return ApiResponse.ok(service.list(userId));
    }

    @GetMapping("/payslip/{id}")
    public ApiResponse<PayslipResponse> get(@PathVariable Long id) {
        boolean privileged = SecurityUtils.hasAuthority("PAYROLL_VIEW");
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId(), id, privileged));
    }

    @GetMapping("/payslip/{id}/pdf")
    public ResponseEntity<ByteArrayResource> downloadPdf(@PathVariable Long id) {
        boolean privileged = SecurityUtils.hasAuthority("PAYROLL_VIEW");
        byte[] bytes = service.pdfBytes(SecurityUtils.currentUserId(), id, privileged);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=payslip-" + id + ".pdf")
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<PayrollRunResponse> startBatch(@Valid @RequestBody PayrollRunRequest req) {
        return ApiResponse.ok(service.generateBatch(req.month(), req.year(), SecurityUtils.currentUserId()), "Payroll run started");
    }

    @PostMapping("/runs/{id}/confirm")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<PayrollRunResponse> confirmRun(@PathVariable Long id) {
        return ApiResponse.ok(service.confirmRun(id, SecurityUtils.currentUserId()), "Payroll run confirmed");
    }

    @PostMapping("/runs/{id}/finance-approve")
    @PreAuthorize("hasAuthority('PAYROLL_APPROVE')")
    public ApiResponse<PayrollRunResponse> financeApproveRun(@PathVariable Long id) {
        return ApiResponse.ok(service.financeApproveRun(id, SecurityUtils.currentUserId()), "Payroll run approved by finance");
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyAuthority('PAYROLL_RUN', 'PAYROLL_APPROVE')")
    public ApiResponse<List<PayrollRunSummary>> listRuns() {
        return ApiResponse.ok(service.listRuns());
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAnyAuthority('PAYROLL_RUN', 'PAYROLL_APPROVE')")
    public ApiResponse<PayrollRunResponse> getRun(@PathVariable Long id) {
        return ApiResponse.ok(service.getRun(id));
    }

    // ============================================================
    // Payslip request workflow
    //   Employee / HR / Manager raise a request -> Admin approves by
    //   filling the customizable form -> requester downloads.
    // ============================================================

    /** Raise a payslip request for a month (any authenticated user). */
    @PostMapping("/requests")
    public ApiResponse<PayslipRequestResponse> raiseRequest(@Valid @RequestBody CreatePayslipRequestDto req) {
        return ApiResponse.ok(
                requestService.raise(SecurityUtils.currentUserId(), req.month(), req.year(), req.note()),
                "Payslip request sent to admin");
    }

    /** The caller's own payslip requests. */
    @GetMapping("/requests/me")
    public ApiResponse<List<PayslipRequestResponse>> myRequests() {
        return ApiResponse.ok(requestService.myRequests(SecurityUtils.currentUserId()));
    }

    /** Admin inbox of payslip requests (defaults to pending only). */
    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<List<PayslipRequestResponse>> inbox(
            @RequestParam(name = "pendingOnly", defaultValue = "true") boolean pendingOnly) {
        return ApiResponse.ok(requestService.adminInbox(pendingOnly));
    }

    /** Admin uploads a company logo to embed in the generated payslip. */
    @PostMapping("/requests/logo")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<Map<String, String>> uploadLogo(@RequestParam("file") MultipartFile file) {
        String path = storageService.store(file, "payslip-logos");
        return ApiResponse.ok(Map.of("path", path), "Logo uploaded");
    }

    /** Admin approves a request by generating the customizable payslip. */
    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<PayslipResponse> approveRequest(
            @PathVariable Long id, @Valid @RequestBody ApprovePayslipRequestDto form) {
        return ApiResponse.ok(
                requestService.approve(SecurityUtils.currentUserId(), id, form),
                "Payslip generated and sent to employee");
    }

    /** Admin rejects a request. */
    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<PayslipRequestResponse> rejectRequest(
            @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        return ApiResponse.ok(
                requestService.reject(SecurityUtils.currentUserId(), id, note),
                "Request rejected");
    }
}
