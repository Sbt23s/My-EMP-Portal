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
    private final ModuleVisibilityService visibility;

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
                "config", service.grid(),
                // Named people per module, and everybody who could be named.
                "people", service.peopleGrid(),
                "candidates", service.candidates(),
                // Who holds each role, by name -- a tick on "HR" reaches three
                // people on this company's data, and the grid could not say who.
                "holders", service.holders()
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

    /**
     * Replace the people named on one module.
     *
     * <p>Separate from the role save because they are separate controls: a
     * module can allow the HR role and additionally name one person, and
     * saving either must not clear the other.
     */
    @PutMapping("/{moduleCode}/people")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<List<Long>> savePeople(
            @PathVariable String moduleCode,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> users = body == null ? List.of() : body.getOrDefault("users", List.of());
        return ApiResponse.ok(
                service.savePeople(moduleCode, users, SecurityUtils.currentUserId()),
                "Recipients updated");
    }

    /**
     * The visibility grid: which Leave Management modules each role sees.
     *
     * <p>A different question from the recipient grid above -- "Team Leaders
     * may be addressed on Permission" and "Team Leaders do not see Work From
     * Home" are separate sentences, and neither expresses the other.
     */
    @GetMapping("/visibility")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<Map<String, Object>> visibilityGrid() {
        return ApiResponse.ok(Map.of(
                "roles", ModuleVisibilityService.ROLES,
                "modules", ModuleVisibilityService.MODULES,
                "config", visibility.grid()
        ));
    }

    /** Replace one role's ticks. An empty list hides every module from it. */
    @PutMapping("/visibility/{roleCode}")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ApiResponse<Map<String, Boolean>> saveVisibility(
            @PathVariable String roleCode,
            @RequestBody Map<String, List<String>> body) {
        List<String> modules = body == null ? List.of() : body.getOrDefault("modules", List.of());
        return ApiResponse.ok(
                visibility.save(roleCode, modules, SecurityUtils.currentUserId()),
                "Module visibility updated");
    }

    /**
     * What the signed-in person may see, for the Leave Management tabs.
     *
     * <p>Not guarded on ORG_MANAGE: every employee's own page asks this about
     * themselves, and it answers with a list of tab names. It is a display
     * preference, and the pages behind those tabs are guarded individually --
     * a tab appearing grants nothing.
     */
    @GetMapping("/visibility/me")
    public ApiResponse<java.util.Set<String>> myVisibleModules() {
        return ApiResponse.ok(visibility.visibleFor(SecurityUtils.currentUserId()));
    }
}
