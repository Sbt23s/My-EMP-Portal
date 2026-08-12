package com.pixous.hrportal.modules.expense;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.expense.dto.TaExpenseRequest;
import com.pixous.hrportal.modules.expense.dto.TaExpenseResponse;
import com.pixous.hrportal.security.SecurityUtils;
import com.pixous.hrportal.common.StorageService;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ta-expenses")
@Tag(name = "Claims", description = "Travel Allowance / Daily Expense entries")
public class TaExpenseController {

    private final TaExpenseService taExpenseService;
    private final StorageService storageService;

    public TaExpenseController(TaExpenseService taExpenseService, StorageService storageService) {
        this.taExpenseService = taExpenseService;
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload petrol slip or travel photo")
    public ApiResponse<Map<String, String>> uploadAttachment(@RequestParam("file") MultipartFile file) {
        String path = storageService.store(file, "ta-attachments");
        return ApiResponse.ok(Map.of("path", path), "Attachment uploaded");
    }

    @PostMapping
    @Operation(summary = "Submit a TA expense entry")
    public ApiResponse<TaExpenseResponse> create(@Valid @RequestBody TaExpenseRequest request) {
        return ApiResponse.ok(taExpenseService.createTaExpense(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit my own claim while it is still pending")
    public ApiResponse<TaExpenseResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody TaExpenseRequest request) {
        return ApiResponse.ok(
                taExpenseService.updateTaExpense(SecurityUtils.currentUserId(), id, request),
                "Claim updated");
    }

    @GetMapping("/me")
    @Operation(summary = "Get my TA expense entries")
    public ApiResponse<List<TaExpenseResponse>> getMyExpenses() {
        return ApiResponse.ok(taExpenseService.getMyTaExpenses(SecurityUtils.currentUserId()));
    }

    @GetMapping("/team")
    @Operation(summary = "Claims raised by the caller's own team — view only, for a Team Leader")
    public ApiResponse<List<TaExpenseResponse>> getTeamExpenses() {
        return ApiResponse.ok(taExpenseService.getMyTeamTaExpenses(SecurityUtils.currentUserId()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','DASHBOARD_EXEC','CLAIM_APPROVE')")
    @Operation(summary = "Get all TA expense entries (for HR/Admin)")
    public ApiResponse<List<TaExpenseResponse>> getAllExpenses() {
        return ApiResponse.ok(taExpenseService.getAllTaExpenses());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','DASHBOARD_EXEC','CLAIM_APPROVE')")
    @Operation(summary = "Approve or reject a TA expense entry")
    public ApiResponse<TaExpenseResponse> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(taExpenseService.updateStatus(
                id, body.get("status"), body.get("comment"), SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a TA expense entry")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        boolean isAdmin = SecurityUtils.hasAuthority("USER_MANAGE") || SecurityUtils.hasAuthority("ORG_MANAGE") || SecurityUtils.hasAuthority("DASHBOARD_EXEC");
        taExpenseService.deleteTaExpense(SecurityUtils.currentUserId(), id, isAdmin);
        return ApiResponse.message("Claim deleted");
    }
}
