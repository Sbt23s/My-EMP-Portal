package com.pixous.hrportal.common;

import com.pixous.hrportal.modules.user.User;

import java.util.Set;

/**
 * The accounts that run the platform rather than work at the company.
 *
 * <p>Three of them: the super administrator, the system administrator and the
 * company head. They hold logins because somebody has to configure the portal
 * and somebody has to be the top of the approval chain, but they are not staff
 * in the sense the rest of the application means it — they have no shift, no
 * salary structure, no team, and nobody raises a disciplinary record about
 * them.
 *
 * <p>The application had not been told that. {@code GET /users} returns every
 * row, so these three appeared in every picker built from it:
 *
 * <ul>
 *   <li>the Discipline screen offered "SADM001 — Super Admin" as somebody to
 *       write up;
 *   <li>Payroll listed them among the employees to set a salary for, and
 *       counted them in "28 without a salary";
 *   <li>the daily attendance digest named them as absent every single day,
 *       because they never punch — on 7 September that was "52 absent", most of
 *       it nobody's absence at all.
 * </ul>
 *
 * <p>Each of those had grown its own copy of the exclusion, or gone without
 * one. This is the single place that answers the question.
 *
 * <h2>Why employee codes</h2>
 *
 * <p>Because that is what the data has. There is no SYSTEM_ADMIN role — the
 * system administrator is identified by the code ADM0001 and nothing else, and
 * the same is true of the other two. Matching on the role would silently miss
 * them. The codes are seeded and stable; the constants below are the only place
 * they need to be written down.
 */
public final class PlatformAccounts {

    /** The company head. Also the final approver, which is why the code recurs. */
    public static final String CTO = "PIX-E100";
    /** The platform administrator, who keeps the portal running. */
    public static final String SYSTEM_ADMIN = "ADM0001";
    /** The super administrator, above the platform administrator. */
    public static final String SUPER_ADMIN = "SADM001";

    private static final Set<String> CODES = Set.of(CTO, SYSTEM_ADMIN, SUPER_ADMIN);

    private PlatformAccounts() {}

    /** True when this employee code belongs to one of the three. */
    public static boolean isPlatformCode(String employeeCode) {
        return employeeCode != null && CODES.contains(employeeCode.trim().toUpperCase());
    }

    /** True when this user is one of the three. */
    public static boolean isPlatform(User u) {
        return u != null && isPlatformCode(u.getEmployeeCode());
    }

    /**
     * True when this user is staff — everybody who is not one of the three.
     *
     * <p>Reads better at a call site that is keeping people rather than
     * dropping them: {@code .filter(PlatformAccounts::isStaff)}.
     */
    public static boolean isStaff(User u) {
        return !isPlatform(u);
    }
}
