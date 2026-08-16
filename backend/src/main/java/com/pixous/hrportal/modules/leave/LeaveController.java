package com.pixous.hrportal.modules.leave;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.leave.dto.*;
import com.pixous.hrportal.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService service;

    @GetMapping("/types")
    public ApiResponse<List<LeaveTypeResponse>> types() {
        return ApiResponse.ok(service.types());
    }

    @PostMapping("/types")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<LeaveTypeResponse> createType(@Valid @RequestBody LeaveTypeRequest req) {
        return ApiResponse.ok(service.createType(req), "Leave type created");
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<LeaveTypeResponse> updateType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest req) {
        return ApiResponse.ok(service.updateType(id, req), "Leave type updated");
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        service.deleteType(id);
        return ApiResponse.message("Leave type deleted");
    }

    /** Loss-of-Pay preview for payslip generation: unpaid leave days + working days. */
    @GetMapping("/lop-preview")
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    public ApiResponse<Map<String, Object>> lopPreview(
            @RequestParam Long userId, @RequestParam int year, @RequestParam int month) {
        return ApiResponse.ok(service.lopPreview(userId, year, month));
    }

    @GetMapping("/balances")
    public ApiResponse<List<LeaveBalanceResponse>> balances(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(service.balances(SecurityUtils.currentUserId(), year));
    }

    /** Admin: allocate default annual leave balances to every employee in one click. */
    @PostMapping("/allocations/apply-defaults")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<Map<String, Integer>> allocateDefaults(@RequestParam(required = false) Integer year) {
        Map<String, Integer> result = service.allocateDefaultsToAll(year);
        return ApiResponse.ok(result, "Leave balances allocated to employees");
    }

    /** Admin: reset an employee's leave balances (used=0) and clear their requests. */
    @PostMapping("/reset/{userId}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> resetUserLeave(@PathVariable Long userId) {
        service.resetUserLeave(userId);
        return ApiResponse.message("Leave reset");
    }

    /** Valid approvers the current user can send a leave of {@code days} to. */
    @GetMapping("/approvers")
    public ApiResponse<List<Map<String, Object>>> leaveApprovers(@RequestParam(defaultValue = "1") double days) {
        return ApiResponse.ok(service.leaveApprovers(SecurityUtils.currentUserId(), days));
    }

    @PostMapping("/apply")
    public ApiResponse<LeaveRequestResponse> apply(@Valid @RequestBody LeaveApplyRequest req) {
        return ApiResponse.ok(service.apply(SecurityUtils.currentUserId(), req), "Leave applied");
    }

    @GetMapping("/me")
    public PageResponse<LeaveRequestResponse> myRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.myRequests(SecurityUtils.currentUserId(), page, size);
    }

    @GetMapping("/my-queue")
    public ApiResponse<List<LeaveRequestResponse>> myQueue() {
        return ApiResponse.ok(service.myQueue(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(SecurityUtils.currentUserId(), id);
        return ApiResponse.message("Leave cancelled");
    }

    // ---- Manager / approver endpoints ----

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public ApiResponse<List<LeaveRequestResponse>> pending() {
        return ApiResponse.ok(service.pendingForManager(SecurityUtils.currentUserId()));
    }

    /** Approver queue across all statuses (Pending/Approved/Rejected tabs). */
    @GetMapping("/requests-for-me")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public ApiResponse<List<LeaveRequestResponse>> requestsForMe() {
        return ApiResponse.ok(service.approverQueue(SecurityUtils.currentUserId()));
    }

    @GetMapping("/on-leave")
    // Any authenticated user can see who is on leave today.
    public ApiResponse<List<LeaveRequestResponse>> onLeave() {
        return ApiResponse.ok(service.onLeaveToday());
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyAuthority('LEAVE_APPROVE','USER_MANAGE','DASHBOARD_EXEC')")
    public ApiResponse<List<LeaveRequestResponse>> calendar(
            @RequestParam String from, @RequestParam String to) {
        return ApiResponse.ok(service.calendar(SecurityUtils.currentUserId(),
                java.time.LocalDate.parse(from), java.time.LocalDate.parse(to)));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public ApiResponse<LeaveRequestResponse> decide(
            @PathVariable Long id, @Valid @RequestBody LeaveDecisionRequest req) {
        return ApiResponse.ok(service.decide(SecurityUtils.currentUserId(), id, req), "Decision saved");
    }

    @PostMapping("/bulk-decision")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE')")
    public ApiResponse<Map<String, Integer>> bulkDecide(@Valid @RequestBody BulkLeaveDecisionRequest req) {
        int count = service.bulkDecide(SecurityUtils.currentUserId(), req);
        return ApiResponse.ok(Map.of("processed", count), "Bulk decision applied");
    }
}
