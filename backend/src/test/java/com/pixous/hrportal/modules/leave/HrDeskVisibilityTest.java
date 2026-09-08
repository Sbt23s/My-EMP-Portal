package com.pixous.hrportal.modules.leave;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HR is a desk, not a person.
 *
 * <p>A permission request goes to whichever HR account the approver list
 * happened to offer, and until now only that account could see it, was told
 * about it, or could decide it. So a request sat unanswered while the HR
 * colleague who was in that day was told they were not allowed to touch it,
 * and an approval one of them made was invisible to the others.
 *
 * <p>The rule that this widens was added deliberately and must not be undone:
 * an administrator override used to let anyone holding SUPER_ADMIN approve any
 * request, which made the approval chain optional. Widening HR is not that —
 * the rung is unchanged, only who counts as standing on it.
 */
class HrDeskVisibilityTest {

    /**
     * The desk.
     *
     * <p>IT_MGR is on it. It was left off at first, on the reasoning that it is
     * a manager role — and the live data says otherwise: the two accounts
     * holding IT_MGR are the HR account and the head of HR, and the account
     * everybody calls "HR" holds IT_MGR and not IT_HR. Excluding it meant the
     * desk-wide rules reached nobody who works the desk.
     */
    private static final Set<String> HR_DESK = Set.of("IT_HR", "CV_HR", "IT_MGR");

    /**
     * Whether this person may decide a request, as
     * {@code PermissionService.decide} judges it.
     *
     * @param addressedTo    the account the request names
     * @param addressedIsHr  whether that account is on the HR desk
     * @param decider        who is trying to decide it
     * @param deciderRoles   the roles they hold
     */
    private static boolean mayDecide(long addressedTo, boolean addressedIsHr,
                                     long decider, Set<String> deciderRoles) {
        boolean direct = addressedTo == decider;
        boolean deciderOnDesk = deciderRoles.stream().anyMatch(HR_DESK::contains);
        return direct || (addressedIsHr && deciderOnDesk);
    }

    @Nested
    @DisplayName("Anyone on the HR desk can act on a request sent to HR")
    class TheDesk {

        @Test
        @DisplayName("The HR colleague it was addressed to can decide it")
        void addressedHr() {
            assertThat(mayDecide(10, true, 10, Set.of("IT_HR"))).isTrue();
        }

        @Test
        @DisplayName("A different HR colleague can decide it too")
        void anotherHr() {
            /*
             * The whole point. Before this, HR account 11 was told "Only the
             * approver this request was sent to can approve or reject it" about
             * a request their own desk had been sent.
             */
            assertThat(mayDecide(10, true, 11, Set.of("IT_HR"))).isTrue();
        }

        @Test
        @DisplayName("The civil-side HR desk counts as well")
        void civilHr() {
            assertThat(mayDecide(10, true, 12, Set.of("CV_HR"))).isTrue();
        }

        @Test
        @DisplayName("The account everybody calls HR holds IT_MGR, and is on the desk")
        void theHrAccountItself() {
            /*
             * The correction this test exists for. The desk was first defined
             * as IT_HR and CV_HR on the reasoning that IT_MGR is a manager
             * role. On this company's data that excluded the HR account itself
             * -- username "hr", which holds IT_MGR alone -- so every rule about
             * "the whole desk" reached nobody who works it.
             */
            assertThat(mayDecide(10, true, 13, Set.of("IT_MGR"))).isTrue();
        }
    }

    @Nested
    @DisplayName("The chain is otherwise unchanged")
    class ChainIntact {

        @Test
        @DisplayName("An administrator still cannot decide somebody else's request")
        void administratorStillRefused() {
            /*
             * The rule this widening must not undo. An override for SUPER_ADMIN
             * was removed on purpose: it let an employee's hours be approved by
             * somebody who had never met them, and let HR be bypassed on a team
             * leader's request. Seeing everything is not deciding everything.
             */
            assertThat(mayDecide(10, true, 99, Set.of("SUPER_ADMIN"))).isFalse();
            assertThat(mayDecide(10, true, 99, Set.of("COMPANY_ADMIN"))).isFalse();
        }

        @Test
        @DisplayName("A team leader's request stays with that team leader")
        void teamLeaderRequestIsNotWidened() {
            /*
             * An employee's request goes to their own team leader, who knows
             * whether the team can spare them. Another team leader must not be
             * able to answer it, and neither must HR -- the request was not
             * sent to them.
             */
            assertThat(mayDecide(20, false, 21, Set.of("IT_TL"))).isFalse();
            assertThat(mayDecide(20, false, 10, Set.of("IT_HR"))).isFalse();
        }

        @Test
        @DisplayName("The team leader it was addressed to still can")
        void addressedTeamLeader() {
            assertThat(mayDecide(20, false, 20, Set.of("IT_TL"))).isTrue();
        }

        @Test
        @DisplayName("An ordinary employee can decide nothing")
        void employee() {
            assertThat(mayDecide(10, true, 30, Set.of("IT_EMP"))).isFalse();
            assertThat(mayDecide(20, false, 30, Set.of("IT_EMP"))).isFalse();
        }
    }

    @Nested
    @DisplayName("Who is told, and how")
    class Notifications {

        /**
         * The recipients {@code apply} builds: the addressed approver always,
         * plus the whole desk when the request went to HR, never the applicant.
         */
        private static List<Long> recipients(long applicant, long addressedTo,
                                             boolean addressedIsHr, List<Long> hrDesk) {
            java.util.LinkedHashSet<Long> out = new java.util.LinkedHashSet<>();
            out.add(addressedTo);
            if (addressedIsHr) {
                out.addAll(hrDesk);
            }
            out.remove(applicant);
            return List.copyOf(out);
        }

        @Test
        @DisplayName("A request to HR reaches the whole desk")
        void wholeDesk() {
            assertThat(recipients(30, 10, true, List.of(10L, 11L, 12L)))
                    .containsExactlyInAnyOrder(10L, 11L, 12L);
        }

        @Test
        @DisplayName("A request to a team leader reaches only them")
        void onlyTheTeamLeader() {
            // Copying every HR account on an employee's three-hour request to
            // their own team leader would make the notification worthless.
            assertThat(recipients(30, 20, false, List.of(10L, 11L, 12L)))
                    .containsExactly(20L);
        }

        @Test
        @DisplayName("The applicant is never told about their own request")
        void notTheApplicant() {
            /*
             * An HR employee asking for permission is on the desk that receives
             * it. Without removing them they would be notified of their own
             * request, which reads as a bug to the person it happens to.
             */
            assertThat(recipients(11, 10, true, List.of(10L, 11L, 12L)))
                    .containsExactlyInAnyOrder(10L, 12L)
                    .doesNotContain(11L);
        }
    }
}
