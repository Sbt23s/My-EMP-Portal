package com.pixous.hrportal.modules.user;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * The extra teams a Team Leader covers, reachable from the places that decide
 * who somebody's approver is.
 *
 * <p>Those decisions live in four places -- {@code LeaveService.sameTeam},
 * {@code TeamMates.sameTeam}, {@code PermissionService} through the latter, and
 * {@code WfhService.resolveApprover} -- and two of them are static. Rather than
 * thread a repository through all four and change signatures the rest of the
 * code depends on, this exposes one static read backed by the injected
 * repository. Four copies of the rule drifting apart is exactly the fault this
 * codebase has already been bitten by.
 *
 * <p>Every method answers "no" when nothing is assigned and when anything goes
 * wrong, so behaviour with an empty table is identical to before it existed.
 */
@Component
@RequiredArgsConstructor
public class ExtraTeams {

    private final TeamLeaderTeamRepository repository;

    private static ExtraTeams instance;

    @PostConstruct
    void register() {
        instance = this;
    }

    /**
     * Whether {@code leaderId} has been assigned the team {@code teamTitle} in
     * addition to their own designation.
     *
     * <p>False for a blank team, for an unassigned leader, and if the lookup
     * fails: an approval rule must never widen because a query threw.
     */
    public static boolean leads(Long leaderId, String teamTitle) {
        if (instance == null || leaderId == null) return false;
        String team = norm(teamTitle);
        if (team == null) return false;
        try {
            return instance.repository.findByUserId(leaderId).stream()
                    .map(t -> norm(t.getTeamTitle()))
                    .anyMatch(team::equals);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** The ids of every leader assigned this team as an extra. Never null. */
    public static List<Long> leaderIdsFor(String teamTitle) {
        if (instance == null) return List.of();
        String team = norm(teamTitle);
        if (team == null) return List.of();
        try {
            return instance.repository.findLeaderIdsForTeam(team);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
    }
}
