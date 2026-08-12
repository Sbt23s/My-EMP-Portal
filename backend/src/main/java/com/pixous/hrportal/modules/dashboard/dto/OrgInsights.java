package com.pixous.hrportal.modules.dashboard.dto;

import java.util.List;
import java.util.Map;

/**
 * The organisation at a glance, for the admin / HR dashboard: who has just
 * joined, who is on probation, who has left, how today's attendance actually
 * looks, and how the company is distributed and growing.
 *
 * <p>Everything here honours the Overall / Digital / Infra choice, so one payload
 * answers the whole page for whichever side is selected.
 */
public record OrgInsights(
        // ---- headline counts ----
        long newJoineesToday,
        long newJoineesThisMonth,
        long workFromHomeToday,
        long onProbation,
        long resigned,
        long upcomingConfirmations,

        // ---- today's attendance, broken down ----
        long presentToday,
        long lateCheckIn,
        long earlyCheckOut,
        long notMarked,

        // ---- the people behind the counts, newest first ----
        List<Person> newJoineeList,
        List<Person> probationList,
        List<Person> resignedList,
        List<Person> confirmationList,

        // ---- how the company is distributed ----
        Map<String, Long> departmentCounts,
        Map<String, Long> teamCounts,
        Map<String, Long> designationCounts,

        /** One entry per month: {month, joined, exited}. Oldest first. */
        List<Map<String, Object>> growthTrend
) {
    /** Just enough of a person to list them and open their record. */
    public record Person(
            Long id,
            String name,
            String employeeCode,
            String team,
            String photoPath,
            /** Joining date, probation end, or relieving date — whichever the list is about. */
            String date,
            /** Days until the date above; negative once it has passed. */
            Integer daysUntil
    ) {}
}
