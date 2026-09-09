package com.pixous.hrportal.modules.approvalconfig;

import com.pixous.hrportal.common.PlatformAccounts;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who each module's "Request to" dropdown may offer.
 *
 * <p>Every such dropdown is assembled by its own service, each with its own
 * hard-coded idea of the answer, so changing it meant changing Java and the
 * lists drifted apart. This is one configurable answer they all consult.
 *
 * <h2>It narrows; it does not produce</h2>
 *
 * <p>The services know things a table cannot: that a complaint raised by HR
 * goes above HR, that an employee's short leave stays with their own team
 * leader, that nobody appears in their own dropdown. Those are routing rules.
 * So each service still builds its candidates and then calls
 * {@link #allows(String, User)} on each one.
 *
 * <h2>Absent means unrestricted</h2>
 *
 * <p>A module with no configuration behaves exactly as it did before this
 * existed. That is what makes the feature safe to ship switched off: the table
 * starts empty, nothing changes, and a mistake in this class cannot empty a
 * dropdown that was working.
 */
@Service
@RequiredArgsConstructor
public class ApprovalRecipientService {

    /** The modules that can be configured, as the admin screen lists them. */
    public static final List<String> MODULES =
            List.of("HELPDESK", "COMPLAINT", "PERMISSION", "LEAVE", "WFH");

    /**
     * The recipients that can be ticked, in the order the screen shows them.
     *
     * <p>CTO is here as a pseudo-role: it is an employee code in this schema
     * and not a row in {@code roles}, but to the person configuring the screen
     * it is the same kind of choice, and splitting the list in two to honour a
     * storage detail would push that detail onto them.
     */
    public static final List<String> RECIPIENT_ROLES =
            List.of("CTO", "SUPER_ADMIN", "COMPANY_ADMIN", "IT_MGR", "IT_HR", "CV_HR", "IT_TL");

    private final ApprovalRecipientConfigRepository repository;

    /**
     * Whether this module may address requests to this person.
     *
     * <p>True when the module has no configuration at all, which is the case
     * for every module until somebody sets one.
     */
    @Transactional(readOnly = true)
    public boolean allows(String moduleCode, User candidate) {
        if (candidate == null) return false;
        Set<String> allowed = allowedRoles(moduleCode);
        if (allowed.isEmpty()) return true;
        return matches(allowed, candidate);
    }

    /**
     * Filter a candidate list a service has already built.
     *
     * <p>The shape most callers want, and it keeps the "unconfigured means
     * unchanged" rule in one place rather than at each call site.
     */
    @Transactional(readOnly = true)
    public List<User> filter(String moduleCode, List<User> candidates) {
        Set<String> allowed = allowedRoles(moduleCode);
        if (allowed.isEmpty()) return candidates;
        return candidates.stream().filter(u -> matches(allowed, u)).toList();
    }

    /** The enabled role codes for a module, upper-cased. Empty when unconfigured. */
    private Set<String> allowedRoles(String moduleCode) {
        if (moduleCode == null) return Set.of();
        return repository.findByModuleCode(moduleCode.toUpperCase()).stream()
                .filter(ApprovalRecipientConfig::isEnabled)
                .map(c -> c.getRoleCode().toUpperCase())
                .collect(Collectors.toSet());
    }

    /** Whether a user satisfies one of the allowed entries. */
    private boolean matches(Set<String> allowed, User u) {
        // CTO first, because it is matched on the employee code rather than on
        // a role -- see RECIPIENT_ROLES.
        if (allowed.contains("CTO") && PlatformAccounts.CTO.equalsIgnoreCase(u.getEmployeeCode())) {
            return true;
        }
        return u.getRoles() != null && u.getRoles().stream()
                .map(Role::getCode)
                .filter(java.util.Objects::nonNull)
                .anyMatch(c -> allowed.contains(c.toUpperCase()));
    }

    // ---- Administration ----

    /**
     * The whole configuration, as the admin screen renders it: every module,
     * every recipient, ticked or not.
     *
     * <p>Built from the full grid rather than from the rows that exist, so an
     * unconfigured module comes back with every box shown and none ticked --
     * which is what it is. Returning only the saved rows would leave the screen
     * to guess the rest.
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Boolean>> grid() {
        Map<String, Set<String>> saved = new LinkedHashMap<>();
        for (ApprovalRecipientConfig c : repository.findAllByOrderByModuleCodeAscRoleCodeAsc()) {
            if (!c.isEnabled()) continue;
            saved.computeIfAbsent(c.getModuleCode().toUpperCase(), k -> new java.util.HashSet<>())
                    .add(c.getRoleCode().toUpperCase());
        }

        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        for (String module : MODULES) {
            Map<String, Boolean> row = new LinkedHashMap<>();
            Set<String> on = saved.getOrDefault(module, Set.of());
            for (String role : RECIPIENT_ROLES) {
                row.put(role, on.contains(role));
            }
            out.put(module, row);
        }
        return out;
    }

    /**
     * Replace one module's configuration with the roles given.
     *
     * <p>A replace rather than a merge, because the screen sends the state of
     * every tick: a role missing from the list is one somebody unticked, and
     * merging would make unticking impossible.
     *
     * <p>An empty list is meaningful and is stored as such — it returns the
     * module to unrestricted, which is the only way to say "offer whatever you
     * would have offered". A module with every box ticked and one with none are
     * the same thing here, and that is fine: both mean no narrowing.
     */
    @Transactional
    public Map<String, Boolean> save(String moduleCode, List<String> roleCodes, Long actorId) {
        String module = moduleCode == null ? "" : moduleCode.trim().toUpperCase();
        if (!MODULES.contains(module)) {
            throw com.pixous.hrportal.common.ApiException.business(
                    "Unknown module: " + moduleCode);
        }
        Set<String> wanted = roleCodes == null ? Set.of() : roleCodes.stream()
                .filter(java.util.Objects::nonNull)
                .map(r -> r.trim().toUpperCase())
                .filter(RECIPIENT_ROLES::contains)
                .collect(Collectors.toSet());

        // Delete then insert, in one transaction. Diffing the two sets would
        // save a handful of writes on a table with at most thirty-five rows,
        // and cost the clarity of "what is stored is what was sent".
        repository.deleteAll(repository.findByModuleCode(module));
        repository.flush();

        for (String role : wanted) {
            ApprovalRecipientConfig c = new ApprovalRecipientConfig();
            c.setModuleCode(module);
            c.setRoleCode(role);
            c.setEnabled(true);
            c.setUpdatedBy(actorId);
            c.setUpdatedAt(LocalDateTime.now());
            repository.save(c);
        }

        Map<String, Boolean> row = new LinkedHashMap<>();
        for (String role : RECIPIENT_ROLES) {
            row.put(role, wanted.contains(role));
        }
        return row;
    }
}
