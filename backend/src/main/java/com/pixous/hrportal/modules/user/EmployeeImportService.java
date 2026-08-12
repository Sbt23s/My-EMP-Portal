package com.pixous.hrportal.modules.user;

import com.pixous.hrportal.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel imports, and undoing them.
 *
 * <p>An import used to be a one-way door: a sheet uploaded by mistake left
 * accounts scattered through the directory with nothing marking them as having
 * come from it, so putting things back meant finding each one by hand. Every
 * import is now recorded and every account it creates points at that record.
 *
 * <p>Undoing one removes only the accounts that import created, and only those
 * that have not started being used. Somebody who has since punched in, applied
 * for leave or been paid is left alone and reported — by then they are a real
 * employee, whatever spreadsheet first introduced them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeImportService {

    private final EmployeeImportRepository importRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final jakarta.persistence.EntityManager entityManager;

    /** Opens a record for an upload about to happen. */
    @Transactional
    public EmployeeImport begin(String fileName, int totalRows, Long actorId) {
        EmployeeImport batch = new EmployeeImport();
        batch.setFileName(fileName == null || fileName.isBlank() ? "Employee sheet" : fileName.trim());
        batch.setImportedBy(actorId);
        batch.setImportedAt(LocalDateTime.now());
        batch.setTotalRows(totalRows);
        return importRepository.save(batch);
    }

    /** Marks an account as having come from this import. */
    @Transactional
    public void stamp(Long userId, Long batchId) {
        if (userId == null || batchId == null) return;
        userRepository.findById(userId).ifPresent(u -> {
            u.setImportBatchId(batchId);
            userRepository.save(u);
        });
    }

    /**
     * Claims accounts that are already in the directory for a sheet, so the sheet
     * can then be removed like any other import.
     *
     * <p>The directory was filled from a sheet before any of this existed. Those
     * accounts carry no batch id, so nothing recorded which sheet each came from,
     * and there was no way to take a sheet's worth of them back out — the only
     * offer was deleting sixty-five people one at a time. HR uploads the sheet
     * again and it is matched against what is already here.
     *
     * <p>Nothing is deleted by this. It writes a batch id and nothing else, and the
     * removal that follows goes through the ordinary path with every one of its
     * guards: anybody who has punched, taken leave or been paid is kept back and
     * named. Matching is on the sheet's own Emp Id against the account's employee
     * code or username, so a row that names nobody claims nobody.
     *
     * <p>An account already belonging to a real import is left alone. Reassigning it
     * would mean the earlier sheet quietly stopped accounting for people it created.
     */
    @Transactional
    public Map<String, Object> adopt(String fileName, List<String> identifiers, Long actorId) {
        List<String> wanted = identifiers == null ? List.of() : identifiers.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase())
                .distinct()
                .toList();
        if (wanted.isEmpty()) {
            throw ApiException.business("That sheet has no Emp Id values to match against.");
        }

        // Every user once, matched in memory. Sixty-five rows, and the hosted
        // database is far enough away that one query beats a hundred and thirty.
        List<User> all = userRepository.findAll();
        Map<String, User> byKey = new LinkedHashMap<>();
        for (User u : all) {
            if (u.getEmployeeCode() != null) byKey.putIfAbsent(u.getEmployeeCode().trim().toUpperCase(), u);
            if (u.getUsername() != null) byKey.putIfAbsent(u.getUsername().trim().toUpperCase(), u);
        }

        List<Map<String, Object>> linked = new ArrayList<>();
        List<Map<String, Object>> alreadyLinked = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        List<User> toStamp = new ArrayList<>();
        for (String key : wanted) {
            User u = byKey.get(key);
            if (u == null) {
                notFound.add(key);
            } else if (u.getImportBatchId() != null) {
                alreadyLinked.add(entry(u, "already belongs to another sheet"));
            } else {
                toStamp.add(u);
            }
        }

        if (toStamp.isEmpty()) {
            throw ApiException.business(
                    "None of that sheet's employees are in the directory as unclaimed accounts. "
                    + notFound.size() + " Emp Id(s) matched nobody, "
                    + alreadyLinked.size() + " already belong to another sheet.");
        }

        EmployeeImport batch = new EmployeeImport();
        batch.setFileName(fileName == null || fileName.isBlank()
                ? "Employee sheet (matched)" : fileName.trim());
        batch.setImportedBy(actorId);
        // The truthful time is when these accounts were claimed, not invented history
        // for when they were created -- that is not recorded anywhere.
        batch.setImportedAt(LocalDateTime.now());
        batch.setTotalRows(wanted.size());
        batch.setCreatedCount(toStamp.size());
        batch.setFailedCount(0);
        batch = importRepository.save(batch);

        for (User u : toStamp) {
            u.setImportBatchId(batch.getId());
            userRepository.save(u);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("name", u.getName());
            row.put("employeeCode", u.getEmployeeCode());
            linked.add(row);
        }

        log.warn("Sheet '{}' matched to {} existing account(s) by user {} ({} unmatched, {} already claimed)",
                batch.getFileName(), linked.size(), actorId, notFound.size(), alreadyLinked.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batchId", batch.getId());
        out.put("fileName", batch.getFileName());
        out.put("linkedCount", linked.size());
        out.put("linked", linked);
        out.put("alreadyLinkedCount", alreadyLinked.size());
        out.put("alreadyLinked", alreadyLinked);
        out.put("notFoundCount", notFound.size());
        out.put("notFound", notFound);
        return out;
    }

    @Transactional
    public void finish(Long batchId, int created, int failed) {
        importRepository.findById(batchId).ifPresent(b -> {
            b.setCreatedCount(created);
            b.setFailedCount(failed);
            importRepository.save(b);
        });
    }

    /** Every import, newest first, with how many of its accounts still exist. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<EmployeeImport> all = importRepository.findAllByOrderByImportedAtDesc();
        List<Map<String, Object>> out = new ArrayList<>();
        for (EmployeeImport b : all) {
            List<User> still = userRepository.findByImportBatchId(b.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("fileName", b.getFileName());
            row.put("importedAt", b.getImportedAt());
            row.put("importedBy", b.getImportedBy() == null ? null
                    : userRepository.findById(b.getImportedBy()).map(User::getName).orElse(null));
            row.put("totalRows", b.getTotalRows());
            row.put("createdCount", b.getCreatedCount());
            row.put("failedCount", b.getFailedCount());
            // What is actually still there — accounts may have been removed since.
            row.put("remaining", still.size());
            row.put("revertedAt", b.getRevertedAt());
            out.add(row);
        }
        return out;
    }

    /**
     * Who would be removed, and who would be kept back — asked before anything
     * is deleted, so the decision is made against names rather than a number.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Long batchId) {
        EmployeeImport batch = importRepository.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("Import"));

        List<Map<String, Object>> removable = new ArrayList<>();
        List<Map<String, Object>> keeping = new ArrayList<>();

        List<User> members = userRepository.findByImportBatchId(batchId);
        // Asked once for the whole sheet. One question per person was what made this
        // take minutes against the hosted database and time out before answering.
        Map<Long, String> reasons = inUseReasons(members.stream().map(User::getId).toList());

        for (User u : members) {
            String reason = reasons.get(u.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("name", u.getName());
            row.put("employeeCode", u.getEmployeeCode());
            row.put("username", u.getUsername());
            if (reason == null) {
                removable.add(row);
            } else {
                row.put("reason", reason);
                keeping.add(row);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", batch.getId());
        out.put("fileName", batch.getFileName());
        out.put("importedAt", batch.getImportedAt());
        out.put("removable", removable);
        out.put("keeping", keeping);
        return out;
    }

    /**
     * Removes the accounts this import created. Each one goes through the same
     * path as deleting an employee by hand, so the dependent rows are cleaned up
     * the same way; one that fails is reported and the rest still go.
     */
    @Transactional
    public Map<String, Object> revert(Long batchId, Long actorId) {
        EmployeeImport batch = importRepository.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("Import"));

        List<User> candidates = userRepository.findByImportBatchId(batchId);
        if (candidates.isEmpty()) {
            throw ApiException.business("This import has no accounts left to remove.");
        }

        List<String> removed = new ArrayList<>();
        List<Map<String, Object>> kept = new ArrayList<>();

        // One question for the whole sheet, before anything is deleted.
        Map<Long, String> reasons = inUseReasons(candidates.stream().map(User::getId).toList());

        for (User u : candidates) {
            // Never remove yourself, whatever created the account.
            if (u.getId().equals(actorId)) {
                kept.add(entry(u, "this is you"));
                continue;
            }
            String reason = reasons.get(u.getId());
            if (reason != null) {
                kept.add(entry(u, reason));
                continue;
            }
            try {
                userService.deleteUser(u.getId());
                removed.add(u.getName() + " (" + u.getEmployeeCode() + ")");
            } catch (Exception e) {
                log.warn("Could not remove imported user {}", u.getId(), e);
                kept.add(entry(u, "could not be removed"));
            }
        }

        batch.setRevertedAt(LocalDateTime.now());
        batch.setRevertedBy(actorId);
        importRepository.save(batch);
        log.warn("Import {} ({}) reverted by user {}: {} removed, {} kept",
                batch.getId(), batch.getFileName(), actorId, removed.size(), kept.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removedCount", removed.size());
        out.put("removed", removed);
        out.put("keptCount", kept.size());
        out.put("kept", kept);
        return out;
    }

    /** Forgets an import that has nothing left, so the list stays readable. */
    @Transactional
    public void forget(Long batchId) {
        EmployeeImport batch = importRepository.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("Import"));
        if (!userRepository.findByImportBatchId(batchId).isEmpty()) {
            throw ApiException.business(
                    "This import still has accounts. Remove them first, or leave the record alone.");
        }
        importRepository.delete(batch);
    }

    private static Map<String, Object> entry(User u, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("name", u.getName());
        m.put("employeeCode", u.getEmployeeCode());
        m.put("reason", reason);
        return m;
    }

    /**
     * The tables that mean somebody has actually started working, in the order the
     * reason is worth reporting: a punch first, a payslip before a chat message.
     */
    private record UseCheck(String table, String column, String reason) {}

    private static final List<UseCheck> USE_CHECKS = List.of(
            new UseCheck("attendance", "user_id", "has attendance records"),
            new UseCheck("leave_requests", "user_id", "has leave requests"),
            new UseCheck("payslips", "user_id", "has payslips"),
            new UseCheck("permission_requests", "user_id", "has permission requests"),
            new UseCheck("work_reports", "user_id", "has work reports"),
            new UseCheck("community_messages", "sender_id", "has sent chat messages"));

    /**
     * Why each of these accounts should be kept, for the ones that should.
     *
     * <p>Asked for the whole batch at once, which is the only reason this finishes.
     * It used to be asked one account at a time, and each of those asked
     * information_schema whether the table existed before counting in it — so a
     * sheet of fifty-eight people cost about seven hundred round trips. Against a
     * database on the same machine that is invisible; against the hosted one it is
     * several minutes, and the request timed out before it could answer. Now it is
     * one query per table, six in total, however many people the sheet has.
     *
     * <p>The existence check stays, because a native statement naming a table that
     * does not exist marks the transaction rollback-only and would abort the whole
     * revert even with the exception caught. It is simply asked once per table
     * rather than once per person per table.
     */
    private Map<Long, String> inUseReasons(List<Long> userIds) {
        Map<Long, String> reasons = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) return reasons;

        for (UseCheck check : USE_CHECKS) {
            if (!hasColumn(check.table(), check.column())) continue;

            // Table and column are hard-coded constants above, never user input.
            @SuppressWarnings("unchecked")
            List<Object> hits = entityManager.createNativeQuery(
                            "SELECT DISTINCT " + check.column() + " FROM " + check.table()
                            + " WHERE " + check.column() + " IN (:ids)")
                    .setParameter("ids", userIds)
                    .getResultList();

            for (Object hit : hits) {
                if (hit == null) continue;
                Long id = ((Number) hit).longValue();
                // First reason wins: the list is ordered by what is worth saying.
                reasons.putIfAbsent(id, check.reason());
            }
        }
        return reasons;
    }

    /** Why one account should be kept, or null when it has never been used. */
    private String inUseReason(Long userId) {
        return inUseReasons(List.of(userId)).get(userId);
    }

    /**
     * Whether a table and column exist, asked once per table for the life of the
     * process. The schema does not change while the application is running, and
     * this was being asked hundreds of times per request.
     */
    private final Map<String, Boolean> columnExists = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean hasColumn(String table, String column) {
        return columnExists.computeIfAbsent(table + "." + column, key -> {
            Number exists = (Number) entityManager.createNativeQuery(
                            "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = :t AND column_name = :c")
                    .setParameter("t", table).setParameter("c", column)
                    .getSingleResult();
            return exists != null && exists.intValue() > 0;
        });
    }

}
