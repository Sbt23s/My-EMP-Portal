package com.pixous.hrportal.modules.user;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.user.dto.EmployeeHistoryRow;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The service record: every employee, when they joined, what changed, and when
 * they left.
 *
 * <p>Its own controller rather than another method on the employee directory,
 * because it answers a different question. The directory says who is here now;
 * this says what has happened. They are read by the same people but for
 * different reasons, and keeping them apart means a change to one cannot
 * disturb the other.
 *
 * <p>Read-only throughout. There is no write path on this controller by design.
 */
@RestController
@RequestMapping("/api/users/history")
@RequiredArgsConstructor
public class EmployeeHistoryController {

    private final EmployeeHistoryService service;

    /**
     * Everyone's service record.
     *
     * <p>Guarded the same way the employee directory is, because it shows the
     * same population: anyone who can see the staff list can see when they
     * joined. It carries no salary and no personal detail beyond what the
     * directory already shows.
     *
     * @param includeRelieved include people who have already left; true by
     *                        default, since the point of a history is that it
     *                        outlives the employment
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ATTENDANCE_TEAM','DASHBOARD_EXEC') or hasRole('TECHNICAL_ADMIN')")
    @Operation(summary = "Service record for every employee")
    public ApiResponse<List<EmployeeHistoryRow>> all(
            @RequestParam(defaultValue = "true") boolean includeRelieved) {
        return ApiResponse.ok(service.all(includeRelieved));
    }

    /** One person's service record. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','ATTENDANCE_TEAM','DASHBOARD_EXEC') or hasRole('TECHNICAL_ADMIN')")
    @Operation(summary = "Service record for one employee")
    public ApiResponse<EmployeeHistoryRow> one(@PathVariable Long id) {
        return ApiResponse.ok(service.one(id)
                .orElseThrow(() -> ApiException.notFound("No such employee.")));
    }
}
