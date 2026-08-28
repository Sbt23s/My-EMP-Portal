package com.pixous.hrportal.modules.leave;

import com.pixous.hrportal.modules.user.ExtraTeams;
import com.pixous.hrportal.modules.user.User;

/**
 * Whether two people are on the same team.
 *
 * <p>Leave and Permission both need to find "the applicant's own Team Leader",
 * and both used to answer it with {@code departmentId}. That was the wrong
 * field. A team in this product is a designation: {@code getMyTeam} groups
 * teammates by designation title or its FK, and the Teams page shows the same
 * grouping. Department is barely populated by comparison, which is why the two
 * screens failed in opposite directions from the same mistake -- Leave matched
 * nobody on department and fell back to listing every Team Leader in the
 * company, while Permission matched nobody and offered an empty dropdown that
 * made the form impossible to submit.
 *
 * <p>Kept here as one shared answer rather than a copy in each service,
 * because the two drifting apart is exactly what produced that pair of bugs.
 */
final class TeamMates {

    private TeamMates() {
    }

    /**
     * True when {@code other} is on {@code me}'s team.
     *
     * <p>Checked in order of how much the field actually means:
     *
     * <ol>
     *   <li>designation title, which is what employee records really carry and
     *       what the Teams page groups by;
     *   <li>the designation FK, for records that carry the link but no title;
     *   <li>department, only for someone with neither of the above -- a last
     *       resort so a half-filled record still resolves to somebody, never a
     *       way to widen a team that already resolved.
     * </ol>
     */
    static boolean sameTeam(User me, User other) {
        String mine = norm(me.getDesignationTitle());

        /*
         * A Team Leader may be assigned teams beyond their own designation, so
         * that a team with no leader of its own -- QA Testing -- still has
         * somebody to approve its requests. Checked before the designation
         * comparison and only ever adds a match: with no assignment recorded
         * this is false and the original rule below decides, unchanged.
         */
        if (mine != null && ExtraTeams.leads(other.getId(), mine)) {
            return true;
        }

        if (mine != null) {
            return mine.equals(norm(other.getDesignationTitle()));
        }

        Long myDesignation = me.getDesignationId();
        if (myDesignation != null) {
            return myDesignation.equals(other.getDesignationId());
        }

        Long myDepartment = me.getDepartmentId();
        return myDepartment != null && myDepartment.equals(other.getDepartmentId());
    }

    /** Whether this person's team can be worked out at all. */
    static boolean hasTeam(User me) {
        return norm(me.getDesignationTitle()) != null
                || me.getDesignationId() != null
                || me.getDepartmentId() != null;
    }

    /** Trimmed and case-folded, so "Flutter Developer" and "flutter developer" are one team. */
    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toLowerCase();
    }
}
