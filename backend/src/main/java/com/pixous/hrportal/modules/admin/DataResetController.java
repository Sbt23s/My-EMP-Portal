package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Clearing the day-to-day records for a fresh start. Restricted to the Super
 * Admin: HR runs the portal, but emptying it is not part of running it.
 *
 * <p>Deliberately NOT extended to COMPANY_ADMIN, which is otherwise treated as
 * the same role everywhere else. Those two hold identical permissions and pass
 * the same checks by design — but "the same access" is not a reason to hand a
 * delete-everything button to more accounts. Widening this should be a decision
 * somebody makes on purpose, not one inherited from an alias.
 */
@RestController
@RequestMapping("/api/admin/reset")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('COMPANY_ADMIN')")
public class DataResetController {

    private final DataResetService service;

    /** What each area holds right now, and what it would leave behind. */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> preview() {
        return ApiResponse.ok(service.preview());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> reset(@RequestBody ResetRequest body) {
        Map<String, Object> result = service.reset(
                body.areas(), body.confirmation(), SecurityUtils.currentUserId());
        return ApiResponse.ok(result, "Cleared " + result.get("total") + " record(s)");
    }

    /** The areas to clear, and the word RESET typed out. */
    public record ResetRequest(java.util.Set<String> areas, String confirmation) {}
}
