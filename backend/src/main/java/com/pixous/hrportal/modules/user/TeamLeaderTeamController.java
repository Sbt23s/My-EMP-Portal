package com.pixous.hrportal.modules.user;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigning a Team Leader to teams beyond their own designation.
 *
 * <p>A separate controller from UserController, which is large and busy: this
 * adds a capability rather than changing how users are managed, and keeping it
 * apart means nothing already working is touched.
 *
 * <p>Reading is open to anyone who can already see the team structure, so the
 * Teams page can show who leads what. Changing an assignment needs the same
 * authority as managing staff.
 */
@RestController
@RequestMapping("/api/team-leaders")
@RequiredArgsConstructor
@Tag(name = "Team Leader teams",
     description = "Which teams a Team Leader covers, beyond their own designation")
public class TeamLeaderTeamController {

    private final TeamLeaderTeamRepository repository;
    private final UserRepository userRepository;

    /** Every extra assignment, as team -> the leaders covering it. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE','ATTENDANCE_TEAM','DASHBOARD_EXEC')")
    @Operation(summary = "All extra team assignments")
    public ApiResponse<List<Map<String, Object>>> all() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TeamLeaderTeam t : repository.findAll()) {
            User u = userRepository.findById(t.getUserId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("userId", t.getUserId());
            row.put("name", u == null ? null : u.getName());
            row.put("code", u == null ? null : u.getEmployeeCode());
            row.put("ownTeam", u == null ? null : u.getDesignationTitle());
            row.put("teamTitle", t.getTeamTitle());
            out.add(row);
        }
        return ApiResponse.ok(out);
    }

    /** The teams one leader covers: their own designation first, then the extras. */
    @GetMapping("/{userId}/teams")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE','ATTENDANCE_TEAM','DASHBOARD_EXEC')")
    @Operation(summary = "Teams one Team Leader covers")
    public ApiResponse<List<String>> teamsOf(@PathVariable Long userId) {
        List<String> teams = new ArrayList<>();
        userRepository.findById(userId).ifPresent(u -> {
            String own = u.getDesignationTitle();
            if (own != null && !own.isBlank()) teams.add(own.trim());
        });
        for (TeamLeaderTeam t : repository.findByUserId(userId)) {
            String extra = t.getTeamTitle() == null ? "" : t.getTeamTitle().trim();
            boolean already = teams.stream().anyMatch(x -> x.equalsIgnoreCase(extra));
            if (!extra.isEmpty() && !already) teams.add(extra);
        }
        return ApiResponse.ok(teams);
    }

    /**
     * Give a Team Leader another team.
     *
     * <p>Refused unless the person actually holds a Team Leader role: an
     * assignment to somebody the approval rules will never consider is a row
     * that silently does nothing.
     */
    @PostMapping("/{userId}/teams")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "Assign another team to a Team Leader")
    @Transactional
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long userId,
                                                   @RequestBody Map<String, String> body) {
        String team = body == null ? null : body.get("teamTitle");
        if (team == null || team.isBlank()) {
            throw com.pixous.hrportal.common.ApiException.business("Choose a team to assign.");
        }
        User u = userRepository.findById(userId)
                .orElseThrow(() -> com.pixous.hrportal.common.ApiException.notFound("User"));

        boolean isTl = u.getRoles() != null && u.getRoles().stream()
                .anyMatch(r -> "IT_TL".equals(r.getCode()) || "CV_SUP".equals(r.getCode()));
        if (!isTl) {
            throw com.pixous.hrportal.common.ApiException.business(
                    u.getName() + " is not a Team Leader, so requests would never be routed to them.");
        }

        String trimmed = team.trim();
        if (repository.existsByUserIdAndTeamTitleIgnoreCase(userId, trimmed)) {
            throw com.pixous.hrportal.common.ApiException.business(
                    u.getName() + " already leads " + trimmed + ".");
        }

        TeamLeaderTeam row = new TeamLeaderTeam();
        row.setUserId(userId);
        row.setTeamTitle(trimmed);
        row.setCreatedBy(SecurityUtils.currentUserId());
        repository.save(row);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("name", u.getName());
        out.put("teamTitle", trimmed);
        return ApiResponse.ok(out, u.getName() + " now leads " + trimmed + ".");
    }

    /** Take a team back off a Team Leader. Their own designation is unaffected. */
    @DeleteMapping("/{userId}/teams")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "Remove an extra team from a Team Leader")
    @Transactional
    public ApiResponse<Void> unassign(@PathVariable Long userId,
                                      @RequestParam String teamTitle) {
        repository.deleteByUserIdAndTeamTitleIgnoreCase(userId, teamTitle.trim());
        return ApiResponse.ok(null, "Removed.");
    }
}
