package com.pixous.hrportal.modules.approvalconfig;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.PlatformAccounts;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which Leave Management modules a role sees.
 *
 * <p>The companion to {@link ApprovalRecipientService}: that decides who a
 * request may be addressed to, this decides which of the five tabs appear at
 * all. Both are needed and neither expresses the other — "Team Leaders may be
 * addressed on Permission" and "Team Leaders do not see Work From Home" are
 * different sentences.
 *
 * <h2>Absent means visible</h2>
 *
 * <p>A role with no rows sees everything it sees today. The table ships empty,
 * so nothing changes until an administrator configures something, and a fault
 * in this class cannot hide a tab that was working.
 *
 * <h2>This is not authorisation</h2>
 *
 * <p>Hiding Approvals from a role that holds LEAVE_APPROVE takes the tab off
 * their screen. It does not stop the endpoint answering them, and it must not:
 * authorisation is decided per request by Spring Security against the
 * permission, and a display preference is not a security boundary. Making one
 * visible grants nothing either — the tab appears and the page inside it
 * refuses.
 */
@Service
@RequiredArgsConstructor
public class ModuleVisibilityService {

    /** The Leave Management tabs, in the order the page shows them. */
    public static final List<String> MODULES =
            List.of("LEAVE", "PERMISSION", "WFH", "APPROVALS", "POLICIES");

    /**
     * The subjects an administrator can configure.
     *
     * <p>CTO is a pseudo-role here as it is in the recipient grid: an employee
     * code rather than a row in {@code roles}, spelled as one so both kinds of
     * subject sit in a single list on screen.
     */
    public static final List<String> ROLES = List.of(
            "IT_EMP", "IT_TL", "IT_HR", "CV_HR", "IT_MGR",
            "CTO", "SUPER_ADMIN", "COMPANY_ADMIN");

    private final RoleModuleVisibilityRepository repository;
    private final UserRepository userRepository;

    /**
     * The modules this person may see.
     *
     * <p>Configured roles are read as a union, not an intersection. Somebody
     * holding IT_EMP and IT_HR is both, and a module hidden from employees but
     * shown to HR is one they should see: the narrower role would otherwise
     * silently cancel a grant made through the wider one, which is the opposite
     * of how roles add up everywhere else in this application.
     *
     * <p>A person all of whose roles are unconfigured sees everything.
     */
    @Transactional(readOnly = true)
    public Set<String> visibleFor(Long userId) {
        if (userId == null) return new LinkedHashSet<>(MODULES);
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return new LinkedHashSet<>(MODULES);

        Set<String> codes = subjectCodes(u);
        if (codes.isEmpty()) return new LinkedHashSet<>(MODULES);

        List<RoleModuleVisibility> rules = repository.findByRoleCodeIn(codes);
        if (rules.isEmpty()) return new LinkedHashSet<>(MODULES);

        Set<String> out = new LinkedHashSet<>();
        for (String module : MODULES) {
            boolean anyConfiguredRoleSpeaksToIt = rules.stream()
                    .anyMatch(r -> r.getModuleCode().equalsIgnoreCase(module));
            if (!anyConfiguredRoleSpeaksToIt) {
                // Nobody said anything about this module, so it stays as it is.
                out.add(module);
                continue;
            }
            boolean shown = rules.stream()
                    .filter(r -> r.getModuleCode().equalsIgnoreCase(module))
                    .anyMatch(RoleModuleVisibility::isVisible);
            if (shown) out.add(module);
        }
        return out;
    }

    /** Role codes plus the CTO pseudo-role, upper-cased. */
    private Set<String> subjectCodes(User u) {
        Set<String> codes = new LinkedHashSet<>();
        if (u.getRoles() != null) {
            u.getRoles().stream()
                    .map(Role::getCode)
                    .filter(java.util.Objects::nonNull)
                    .forEach(c -> codes.add(c.toUpperCase()));
        }
        if (PlatformAccounts.CTO.equalsIgnoreCase(u.getEmployeeCode())) {
            codes.add("CTO");
        }
        return codes;
    }

    // ---- Administration ----

    /**
     * The whole grid: every role, every module, shown or hidden.
     *
     * <p>Built from the full grid rather than from the rows that exist, so an
     * unconfigured role comes back with every module ticked — which is what it
     * is. Returning only the saved rows would leave the screen to guess the
     * rest, and it would guess "hidden".
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Boolean>> grid() {
        Map<String, Map<String, Boolean>> saved = new LinkedHashMap<>();
        for (RoleModuleVisibility r : repository.findAllByOrderByRoleCodeAscModuleCodeAsc()) {
            saved.computeIfAbsent(r.getRoleCode().toUpperCase(), k -> new LinkedHashMap<>())
                    .put(r.getModuleCode().toUpperCase(), r.isVisible());
        }

        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        for (String role : ROLES) {
            Map<String, Boolean> row = new LinkedHashMap<>();
            Map<String, Boolean> on = saved.getOrDefault(role, Map.of());
            for (String module : MODULES) {
                // Unconfigured is visible, so an untouched role reads as all on.
                row.put(module, on.getOrDefault(module, true));
            }
            out.put(role, row);
        }
        return out;
    }

    /**
     * Replace one role's visibility with the modules given.
     *
     * <p>A replace rather than a merge, because the screen holds the state of
     * every tick and a module missing from the list is one somebody unticked.
     *
     * <p>Rows are written for hidden modules too, as {@code visible = false},
     * rather than only for the visible ones. Absence has to keep meaning
     * "nobody has configured this role" — if hiding were expressed by deleting,
     * a role with everything hidden would be indistinguishable from one nobody
     * had touched, and would come back showing everything.
     */
    @Transactional
    public Map<String, Boolean> save(String roleCode, List<String> modules, Long actorId) {
        String role = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if (!ROLES.contains(role)) {
            throw ApiException.business("Unknown role: " + roleCode);
        }
        Set<String> wanted = modules == null ? Set.of() : modules.stream()
                .filter(java.util.Objects::nonNull)
                .map(m -> m.trim().toUpperCase())
                .filter(MODULES::contains)
                .collect(Collectors.toSet());

        // Delete then insert, in one transaction. Diffing would save a handful
        // of writes on a table of at most forty rows and cost the clarity of
        // "what is stored is what was sent".
        repository.deleteAll(repository.findByRoleCode(role));
        repository.flush();

        Map<String, Boolean> row = new LinkedHashMap<>();
        for (String module : MODULES) {
            boolean visible = wanted.contains(module);
            RoleModuleVisibility v = new RoleModuleVisibility();
            v.setRoleCode(role);
            v.setModuleCode(module);
            v.setVisible(visible);
            v.setUpdatedBy(actorId);
            v.setUpdatedAt(LocalDateTime.now());
            repository.save(v);
            row.put(module, visible);
        }
        return row;
    }
}
