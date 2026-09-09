package com.pixous.hrportal.modules.approvalconfig;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The administrator's approval configuration: which roles each module may
 * address a request to.
 *
 * <p>Guarded on ORG_MANAGE throughout, read included. This decides who sees
 * whose complaints and who can approve whose leave, so the grid is not
 * something an employee should be able to read off the API and reason about.
 */
@RestController
@RequestMapping("/api/admin/approval-config")
@RequiredArgsConstructor
public class ApprovalRecipientController {

    private final ApprovalRecipientService service;

    /**
     * The whole grid: every module, every recipient, ticked or not.
     *
     * <p>The choices come with it rather than being hard-coded in the screen,
     * so adding a module or a recipient role is one change here and not two.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<Map<String, Object>> grid() {
        return ApiResponse.ok(Map.of(
                "modules", ApprovalRecipientService.MODULES,
                "roles", ApprovalRecipientService.RECIPIENT_ROLES,
                "config", service.grid()
        ));
    }

    /**
     * Replace one module's ticks.
     *
     * <p>Whole-module rather than per-tick: the screen holds the state of every
     * box, and sending them one at a time would leave a half-saved grid if the
     * page were closed midway.
     *
     * <p>An empty list is a valid body and means "no restriction" — that is how
     * a module is returned to offering whatever it would have offered.
     */
    @PutMapping("/{moduleCode}")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<Map<String, Boolean>> save(
            @PathVariable String moduleCode,
            @RequestBody Map<String, List<String>> body) {
        List<String> roles = body == null ? List.of() : body.getOrDefault("roles", List.of());
        return ApiResponse.ok(
                service.save(moduleCode, roles, SecurityUtils.currentUserId()),
                "Approval recipients updated");
    }
}
