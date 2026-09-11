package com.pixous.hrportal.modules.user.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One employee's service record: when they joined, what happened along the way,
 * and when they left if they have.
 *
 * <p>Assembled from what the portal already knows rather than from a new store —
 * the joining date and probation end on the user row, the employment status, the
 * relieving date from offboarding, and the dated events below. Nothing here is
 * written by this feature; it only reads.
 *
 * @param events every dated thing known about this person, oldest first
 */
public record EmployeeHistoryRow(
        Long id,
        String employeeCode,
        String name,
        String email,
        String phone,
        String designationTitle,
        String departmentName,
        List<String> roles,

        /** The day they started. Present for everyone. */
        LocalDate dateOfJoining,

        /** When probation ended, where one was set. */
        LocalDate probationEndDate,

        /** Permanent, Probation, Contract, Intern, Daily Wage — or null if never set. */
        String employmentStatus,

        /** ACTIVE, INACTIVE, RELIEVED. */
        String profileStatus,

        /** The last day, for someone who has left. Null for current staff. */
        LocalDate relievingDate,

        /** Why they left, where it was recorded. */
        String relievingReason,

        /** Full and final settlement: PENDING, CLEARED, or null. */
        String fnfStatus,

        /**
         * Completed years and months between joining and either the relieving
         * date or today. Rendered by the caller; kept as a number here so a
         * report can sort by it.
         */
        Integer tenureMonths,

        List<HistoryEvent> events
) {
    /**
     * A dated entry in someone's service record.
     *
     * @param type   JOINED, PROBATION_END, STATUS_CHANGE, ROLE_CHANGE,
     *               SALARY_CHANGE, RELIEVED
     * @param detail what changed, in words
     * @param actor  who made the change, where the audit log recorded it
     */
    public record HistoryEvent(
            LocalDate date,
            String type,
            String detail,
            String actor
    ) {}
}
