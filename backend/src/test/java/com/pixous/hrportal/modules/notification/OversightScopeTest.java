package com.pixous.hrportal.modules.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who is allowed to read a request addressed to somebody else.
 *
 * <p>A complaint and a support ticket both name their recipient, and the
 * choice between HR and the CTO is the point of the field: somebody
 * complaining about HR addresses it to the CTO precisely so HR does not read
 * it.
 *
 * <p>This is pinned as a test because it has already been got wrong twice, in
 * opposite directions. First the queue returned everything to anyone holding
 * COMPLAINT_MANAGE. Then the fix carved out an exception for USER_MANAGE, on
 * the reasoning that a platform administrator needs the whole queue — but HR
 * holds USER_MANAGE too, for managing employee records, so the exception
 * handed HR back exactly what had just been taken away.
 *
 * <p>The rule is therefore about accounts, not permissions: the permissions do
 * not separate these people and no combination of them does.
 */
class OversightScopeTest {

    private static final String CTO = "PIX-E100";
    private static final String SYSTEM_ADMIN = "ADM0001";

    /** The rule as OversightNotifier applies it. */
    private static boolean seesEveryRequest(String employeeCode) {
        return CTO.equalsIgnoreCase(employeeCode) || SYSTEM_ADMIN.equalsIgnoreCase(employeeCode);
    }

    @Test
    @DisplayName("The CTO reads every request")
    void ctoSeesAll() {
        assertThat(seesEveryRequest("PIX-E100")).isTrue();
        assertThat(seesEveryRequest("pix-e100")).isTrue();
    }

    @Test
    @DisplayName("The platform administrator reads every request")
    void adminSeesAll() {
        // A queue they cannot see is a queue they cannot repair.
        assertThat(seesEveryRequest("ADM0001")).isTrue();
    }

    @Test
    @DisplayName("HR does not, however many permissions they hold")
    void hrDoesNot() {
        // HR holds USER_MANAGE, COMPLAINT_MANAGE and HELPDESK_AGENT. None of
        // them is the question being asked here.
        assertThat(seesEveryRequest("HR0001")).isFalse();
    }

    @Test
    @DisplayName("An ordinary employee does not")
    void employeeDoesNot() {
        assertThat(seesEveryRequest("PIX-E057")).isFalse();
    }

    @Test
    @DisplayName("An unknown or missing code is not privileged")
    void unknownIsNotPrivileged() {
        // Failing open here would expose the whole queue to anybody whose
        // account could not be read.
        assertThat(seesEveryRequest(null)).isFalse();
        assertThat(seesEveryRequest("")).isFalse();
    }
}
