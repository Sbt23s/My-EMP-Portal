package com.pixous.hrportal.modules.user;

import com.pixous.hrportal.modules.audit.AuditEntry;
import com.pixous.hrportal.modules.audit.AuditEntryRepository;
import com.pixous.hrportal.modules.org.EmploymentStatusRepository;
import com.pixous.hrportal.modules.user.dto.EmployeeHistoryRow;
import com.pixous.hrportal.modules.user.dto.EmployeeHistoryRow.HistoryEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The service record for every employee: when they joined, what changed along
 * the way, and when they left.
 *
 * <p>This reads; it never writes. Everything it returns is already held
 * somewhere in the portal — the joining date and probation end on the user row,
 * the employment status on the organisation tables, the relieving date on the
 * offboarding record, and the dated changes in the audit log. Assembling them
 * in one place is the whole of the feature.
 *
 * <p>Two things are deliberately not done here. There is no new table, because
 * a history built from a second copy of the facts drifts from the first. And
 * nothing is inferred that is not recorded: an employee with no employment
 * status set shows no status rather than a guess, because a guessed career
 * stage on a service record is worse than a blank one.
 */
@Service
@RequiredArgsConstructor
public class EmployeeHistoryService {

    private final UserRepository userRepository;
    private final OffboardingRecordRepository offboardingRepository;
    private final EmploymentStatusRepository employmentStatusRepository;
    private final AuditEntryRepository auditRepository;

    /**
     * Every employee's service record, newest joiner first.
     *
     * <p>Platform accounts are excluded, as they are from every other roll:
     * they are not employees and a joining date for them means nothing.
     *
     * @param includeRelieved when false, people who have left are left out
     */
    @Transactional(readOnly = true)
    public List<EmployeeHistoryRow> all(boolean includeRelieved) {
        Map<Long, String> statusById = employmentStatusRepository.findAll().stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));

        // One query for every offboarding record rather than one per employee:
        // there are as many employees as there are rows on the page, and a
        // lookup each would be the classic N+1 on a screen built to show all.
        Map<Long, OffboardingRecord> offboardingByUser = offboardingRepository.findAll().stream()
                .collect(Collectors.toMap(OffboardingRecord::getUserId, Function.identity(), (a, b) -> a));

        List<EmployeeHistoryRow> rows = new ArrayList<>();

        for (User u : userRepository.findAll()) {
            if (com.pixous.hrportal.common.PlatformAccounts.isPlatform(u)) continue;

            OffboardingRecord off = offboardingByUser.get(u.getId());
            LocalDate relieved = off != null ? off.getRelievingDate() : null;

            boolean hasLeft = relieved != null && !relieved.isAfter(LocalDate.now());
            if (hasLeft && !includeRelieved) continue;

            rows.add(new EmployeeHistoryRow(
                    u.getId(),
                    u.getEmployeeCode(),
                    displayName(u),
                    u.getEmail(),
                    u.getPhone(),
                    blankToNull(u.getDesignationTitle()),
                    blankToNull(u.getDepartmentTitle()),
                    u.getRoles().stream().map(r -> r.getCode()).sorted().toList(),
                    u.getDateOfJoining(),
                    u.getProbationEndDate(),
                    u.getEmploymentStatusId() != null ? statusById.get(u.getEmploymentStatusId()) : null,
                    u.getProfileStatus(),
                    relieved,
                    off != null ? blankToNull(off.getReason()) : null,
                    off != null ? off.getFnfStatus() : null,
                    tenureMonths(u.getDateOfJoining(), relieved),
                    events(u, off)
            ));
        }

        // Newest joiner first, and anyone with no joining date last rather than
        // first — a missing date is not a recent one.
        rows.sort(Comparator.comparing(
                EmployeeHistoryRow::dateOfJoining,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    /** One person's record, or empty if there is no such employee. */
    @Transactional(readOnly = true)
    public Optional<EmployeeHistoryRow> one(Long userId) {
        return all(true).stream().filter(r -> r.id().equals(userId)).findFirst();
    }

    // ---- the dated events ----

    /**
     * Everything dated that is known about this person, oldest first.
     *
     * <p>Joining and relieving come from the record itself. The changes in
     * between come from the audit log, which has been recording them since it
     * was switched on — so a long-serving employee may show a joining date in
     * 2023 and their first logged change much later. That gap is honest: the
     * portal does not know what happened before it was watching, and inventing
     * events to fill it would make the record look complete when it is not.
     */
    private List<HistoryEvent> events(User u, OffboardingRecord off) {
        List<HistoryEvent> out = new ArrayList<>();

        if (u.getDateOfJoining() != null) {
            out.add(new HistoryEvent(u.getDateOfJoining(), "JOINED",
                    "Joined the company", null));
        }
        if (u.getProbationEndDate() != null) {
            out.add(new HistoryEvent(u.getProbationEndDate(), "PROBATION_END",
                    "Probation ended", null));
        }

        for (AuditEntry a : auditRepository.findByEntityTypeAndEntityIdOrderByAtDesc(
                "USER", String.valueOf(u.getId()))) {
            String summary = blankToNull(a.getSummary());
            if (summary == null) continue;
            out.add(new HistoryEvent(
                    a.getAt().toLocalDate(),
                    "CHANGE",
                    summary,
                    blankToNull(a.getUserName())));
        }

        if (off != null && off.getRelievingDate() != null) {
            String why = blankToNull(off.getReason());
            out.add(new HistoryEvent(off.getRelievingDate(), "RELIEVED",
                    why != null ? "Relieved — " + why : "Relieved", null));
        }

        out.sort(Comparator.comparing(HistoryEvent::date));
        return out;
    }

    // ---- small helpers ----

    /**
     * Completed months between joining and leaving, or between joining and
     * today for someone still here. Null when there is no joining date to
     * count from.
     */
    private Integer tenureMonths(LocalDate joined, LocalDate relieved) {
        if (joined == null) return null;
        LocalDate end = relieved != null ? relieved : LocalDate.now();
        if (end.isBefore(joined)) return 0;
        return (int) ChronoUnit.MONTHS.between(joined, end);
    }

    private static String displayName(User u) {
        String name = u.getName();
        return name != null && !name.isBlank() ? name : u.getUsername();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
