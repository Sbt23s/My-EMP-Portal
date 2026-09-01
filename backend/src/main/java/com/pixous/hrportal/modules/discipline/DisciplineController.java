package com.pixous.hrportal.modules.discipline;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.discipline.dto.DisciplineDtos;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Disciplinary records.
 *
 * <ul>
 *   <li>HR and administrators raise, correct and withdraw them.</li>
 *   <li>The employee reads the ones about themselves and may answer.</li>
 *   <li>The CTO reviews them and writes the remark the employee is shown.</li>
 * </ul>
 *
 * <p>The role checks here decide who may call at all; the service decides what
 * each caller may see, because "my own records" is a question about the rows
 * rather than about the endpoint.
 */
@RestController
@RequestMapping("/api/discipline")
@RequiredArgsConstructor
@Tag(name = "Discipline", description = "Disciplinary records raised by HR and reviewed by the CTO")
public class DisciplineController {

    private final DisciplineService service;
    private final com.pixous.hrportal.common.StorageService storageService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE')")
    @Operation(summary = "HR/Admin: raise a discipline record about an employee")
    public ApiResponse<DisciplineDtos.View> create(@Valid @RequestBody DisciplineDtos.CreateRequest req) {
        return ApiResponse.ok(service.create(SecurityUtils.currentUserId(), req), "Discipline record created");
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "HR/Admin/CTO: every discipline record")
    public ApiResponse<List<DisciplineDtos.View>> all(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size) {
        return ApiResponse.ok(service.all(status, page, size));
    }

    /** The records about the person asking. Any authenticated user. */
    @GetMapping("/mine")
    @Operation(summary = "The current user's own discipline records")
    public ApiResponse<List<DisciplineDtos.View>> mine() {
        return ApiResponse.ok(service.mine(SecurityUtils.currentUserId()));
    }

    @GetMapping("/pending-review")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "CTO: records still waiting on a review")
    public ApiResponse<List<DisciplineDtos.View>> pendingReview() {
        return ApiResponse.ok(service.pendingReview());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One record, if it is yours to read")
    public ApiResponse<DisciplineDtos.View> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE')")
    @Operation(summary = "HR/Admin: correct a record")
    public ApiResponse<DisciplineDtos.View> update(
            @PathVariable Long id, @Valid @RequestBody DisciplineDtos.UpdateRequest req) {
        return ApiResponse.ok(service.update(SecurityUtils.currentUserId(), id, req), "Updated");
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE')")
    @Operation(summary = "HR/Admin: withdraw a record")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(SecurityUtils.currentUserId(), id);
        return ApiResponse.message("Discipline record withdrawn");
    }

    /** The employee's own answer. Guarded in the service, not here: the rule is
     *  "a record about you", which is a fact about the row. */
    @PostMapping("/{id}/response")
    @Operation(summary = "Employee: respond to a record about yourself")
    public ApiResponse<DisciplineDtos.View> respond(
            @PathVariable Long id, @Valid @RequestBody DisciplineDtos.ResponseRequest req) {
        return ApiResponse.ok(service.respond(SecurityUtils.currentUserId(), id, req), "Response saved");
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','DASHBOARD_EXEC')")
    @Operation(summary = "CTO: add remarks and set where the record stands")
    public ApiResponse<DisciplineDtos.View> review(
            @PathVariable Long id, @RequestBody DisciplineDtos.ReviewRequest req) {
        return ApiResponse.ok(service.review(SecurityUtils.currentUserId(), id, req), "Review saved");
    }

    /** An attachment for a record, stored the way tickets and leave store theirs. */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','COMPLAINT_MANAGE')")
    @Operation(summary = "HR/Admin: upload evidence for a record")
    public ApiResponse<java.util.Map<String, String>> upload(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ApiResponse.ok(
                java.util.Map.of("path", storageService.store(file, "discipline-attachments")),
                "Uploaded");
    }

}
