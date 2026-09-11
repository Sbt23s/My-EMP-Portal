package com.pixous.hrportal.modules.approvalconfig;

import com.pixous.hrportal.common.PlatformAccounts;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
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
    private final UserRepository userRepository;

    /**
     * Whether this module may address requests to this person.
     *
     * <p>True when the module has no configuration at all, which is the case
     * for every module until somebody sets one.
     */
    @Transactional(readOnly = true)
    public boolean allows(String moduleCode, User candidate) {
        if (candidate == null) return false;
        Rules rules = rulesFor(moduleCode);
        if (rules.isEmpty()) return true;
        return matches(rules, candidate);
    }

    /**
     * Filter a candidate list a service has already built.
     *
     * <p>The shape most callers want, and it keeps the "unconfigured means
     * unchanged" rule in one place rather than at each call site.
     */
    @Transactional(readOnly = true)
    public List<User> filter(String moduleCode, List<User> candidates) {
        Rules rules = rulesFor(moduleCode);
        if (rules.isEmpty()) return candidates;
        return candidates.stream().filter(u -> matches(rules, u)).toList();
    }

    /**
     * What a module is configured with: role codes, named people, or both.
     *
     * <p>Empty on both counts means unconfigured, and unconfigured means
     * unrestricted -- see the class note.
     */
    private record Rules(Set<String> roles, Set<Long> users) {
        boolean isEmpty() { return roles.isEmpty() && users.isEmpty(); }
    }

    private Rules rulesFor(String moduleCode) {
        if (moduleCode == null) return new Rules(Set.of(), Set.of());
        List<ApprovalRecipientConfig> rows =
                repository.findByModuleCode(moduleCode.toUpperCase()).stream()
                        .filter(ApprovalRecipientConfig::isEnabled)
                        .toList();
        Set<String> roles = rows.stream()
                .map(ApprovalRecipientConfig::getRoleCode)
                .filter(java.util.Objects::nonNull)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        Set<Long> users = rows.stream()
                .map(ApprovalRecipientConfig::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return new Rules(roles, users);
    }

    /**
     * Whether a user satisfies the configuration.
     *
     * <p>Role rules and person rules are alternatives, not conditions to be met
     * together: naming Elandevan on a module that also allows the HR role
     * offers both, rather than offering nobody because he is not both at once.
     */
    private boolean matches(Rules rules, User u) {
        if (u.getId() != null && rules.users().contains(u.getId())) return true;
        Set<String> allowed = rules.roles();
        // CTO before the role check, because it is matched on the employee code
        // rather than on a role -- see RECIPIENT_ROLES.
        if (allowed.contains("CTO") && PlatformAccounts.CTO.equalsIgnoreCase(u.getEmployeeCode())) {
            return true;
        }
        return u.getRoles() != null && u.getRoles().stream()
                .map(Role::getCode)
                .filter(java.util.Objects::nonNull)
                .anyMatch(c -> allowed.contains(c.toUpperCase()));
    }

    /**
     * Who actually holds each configurable role, by name.
     *
     * <p>The grid showed role codes -- "HR (Manager)", "HR", "Team Leader" --
     * and an administrator ticking one could not tell who that was. HR is three
     * accounts on this company's data, and which people a tick reaches is the
     * whole question being answered.
     *
     * <p>Disabled and offboarded accounts are left out: a name on this screen
     * is a person a request could be sent to, and neither of those can be.
     *
     * <p>An empty list against a role is worth showing rather than hiding. It
     * says "nobody holds this yet", which is a real answer and stops a tick
     * that would silently do nothing.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> holders() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String role : RECIPIENT_ROLES) {
            List<User> people = "CTO".equals(role)
                    // CTO is an employee code, not a role -- see RECIPIENT_ROLES.
                    ? userRepository.findByEmployeeCode(PlatformAccounts.CTO)
                            .map(List::of).orElseGet(List::of)
                    : userRepository.findByRoleCodes(List.of(role));

            out.put(role, people.stream()
                    .filter(User::isEnabled)
                    .filter(u -> !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                    .map(u -> u.getName() == null ? "" : u.getName().trim())
                    .filter(n -> !n.isEmpty())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .distinct()
                    .toList());
        }
        return out;
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
            // Person rows have no role and are reported by peopleGrid().
            if (c.getRoleCode() == null) continue;
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
     * The people named on each module, by id.
     *
     * <p>Separate from {@link #grid} because they are a different kind of rule
     * and the screen shows them in a different control -- a picker of names
     * rather than a row of role checkboxes.
     */
    @Transactional(readOnly = true)
    public Map<String, List<Long>> peopleGrid() {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        for (String module : MODULES) out.put(module, new java.util.ArrayList<>());
        for (ApprovalRecipientConfig c : repository.findAllByOrderByModuleCodeAscRoleCodeAsc()) {
            if (!c.isEnabled() || c.getUserId() == null) continue;
            out.computeIfAbsent(c.getModuleCode().toUpperCase(), k -> new java.util.ArrayList<>())
                    .add(c.getUserId());
        }
        return out;
    }

    /**
     * Everybody who could be named on a module, with the roles they hold.
     *
     * <p>The picker needs people rather than roles, and it needs enough beside
     * each name to tell two colleagues apart -- the employee code and what they
     * are on the desk. Offboarded and disabled accounts are left out: a name
     * here is somebody a request could be sent to.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> candidates() {
        Set<String> wanted = new java.util.LinkedHashSet<>(RECIPIENT_ROLES);
        wanted.remove("CTO");
        List<User> people = new java.util.ArrayList<>(userRepository.findByRoleCodes(wanted));
        // The CTO is matched by employee code, not by a role, so they are not
        // in that query and would otherwise be unpickable.
        userRepository.findByEmployeeCode(PlatformAccounts.CTO).ifPresent(people::add);

        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (User u : people) {
            if (!u.isEnabled() || "OFFBOARDED".equalsIgnoreCase(u.getProfileStatus())) continue;
            if (byId.containsKey(u.getId())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getName() == null ? "" : u.getName().trim());
            row.put("code", u.getEmployeeCode() == null ? "" : u.getEmployeeCode());
            row.put("roles", u.getRoles() == null ? List.of() : u.getRoles().stream()
                    .map(Role::getCode).filter(java.util.Objects::nonNull).sorted().toList());
            byId.put(u.getId(), row);
        }
        return byId.values().stream()
                .sorted(java.util.Comparator.comparing(
                        m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Replace the people named on one module.
     *
     * <p>Leaves the module's role rules alone: the two are separate controls on
     * screen and saving one must not clear the other. An empty list removes
     * every name, which returns the module to whatever its role rules say --
     * and to unrestricted if it has none.
     */
    @Transactional
    public List<Long> savePeople(String moduleCode, List<Long> userIds, Long actorId) {
        String module = moduleCode == null ? "" : moduleCode.trim().toUpperCase();
        if (!MODULES.contains(module)) {
            throw com.pixous.hrportal.common.ApiException.business("Unknown module: " + moduleCode);
        }
        // Only real, addressable accounts -- an id typed into a request body
        // must not create a rule naming somebody who left. Resolved in one
        // query rather than one per id, and the caller's order is preserved
        // because the ids drive the result rather than the lookup.
        Set<Long> addressable = userIds == null || userIds.isEmpty()
                ? Set.of()
                : userRepository.findAllById(
                                userIds.stream().filter(java.util.Objects::nonNull).toList())
                        .stream()
                        .filter(User::isEnabled)
                        .filter(u -> !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                        .map(User::getId)
                        .collect(java.util.stream.Collectors.toSet());

        Set<Long> wanted = userIds == null ? Set.of() : userIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(addressable::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        repository.deleteAll(repository.findByModuleCode(module).stream()
                .filter(c -> c.getUserId() != null)
                .toList());
        repository.flush();

        for (Long id : wanted) {
            ApprovalRecipientConfig c = new ApprovalRecipientConfig();
            c.setModuleCode(module);
            c.setUserId(id);
            c.setEnabled(true);
            c.setUpdatedBy(actorId);
            c.setUpdatedAt(LocalDateTime.now());
            repository.save(c);
        }
        return List.copyOf(wanted);
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
        // Role rows only. The people named on this module are a separate
        // control on screen, and saving one must not clear the other.
        repository.deleteAll(repository.findByModuleCode(module).stream()
                .filter(c -> c.getRoleCode() != null)
                .toList());
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
