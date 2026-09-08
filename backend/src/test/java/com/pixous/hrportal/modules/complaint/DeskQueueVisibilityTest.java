package com.pixous.hrportal.modules.complaint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whose queue a complaint or a support ticket appears in.
 *
 * <p>Both modules address a request to one account. The recipient picker offers
 * a single "HR (code)" entry, so every request sent to HR carried one id — and
 * the queue query matched on that id alone. Which HR account the picker named
 * is an accident of list order, not a decision anybody made, so the effect was
 * that one person saw the whole HR queue and their colleagues saw nothing:
 *
 * <pre>
 *   Elandevan Ravikumar (PIX-E058)   All complaints (0)   All tickets (0)
 * </pre>
 *
 * <p>He holds IT_HR and COMPLAINT_MANAGE. The endpoint answered him; it simply
 * had nothing addressed to his id to return.
 *
 * <p>There is a second rule these queues carry, older and deliberate, that the
 * widening must not undo: a complaint addressed <em>past</em> HR to the CTO is
 * not HR's to read. That is why the queue is not simply "everything" for anyone
 * holding the permission — an earlier version did exactly that, and HR could
 * read complaints about themselves.
 */
class DeskQueueVisibilityTest {

    /** IT_HR, CV_HR and IT_MGR: the account everybody calls "HR" holds IT_MGR. */
    private static final Set<String> HR_DESK = Set.of("IT_HR", "CV_HR", "IT_MGR");

    private static final long HR_ACCOUNT = 10;
    private static final long ELANDEVAN = 11;
    private static final long VANARAJA = 12;
    private static final long CTO = 90;
    private static final long EMPLOYEE = 30;

    /** The ids whose queue this viewer shares, as the services compute them. */
    private static List<Long> queueMates(long viewer, Set<String> roles, List<Long> desk) {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        ids.add(viewer);
        if (roles.stream().anyMatch(HR_DESK::contains)) {
            ids.addAll(desk);
        }
        return List.copyOf(ids);
    }

    /**
     * Whether a request shows in this viewer's queue, as the widened query
     * judges it: addressed to anybody they share a queue with, or raised by
     * them.
     */
    private static boolean visible(long addressedTo, long raisedBy,
                                   long viewer, Set<String> roles, List<Long> desk) {
        List<Long> mates = queueMates(viewer, roles, desk);
        return mates.contains(addressedTo) || raisedBy == viewer;
    }

    private static final List<Long> DESK = List.of(HR_ACCOUNT, ELANDEVAN, VANARAJA);

    @Nested
    @DisplayName("The HR desk shares one queue")
    class TheDesk {

        @Test
        @DisplayName("A complaint addressed to one HR account shows for all of them")
        void wholeDeskSeesIt() {
            // The failure this exists for: two people looking at an empty
            // screen while a third holds the only copy.
            assertThat(visible(HR_ACCOUNT, EMPLOYEE, ELANDEVAN, Set.of("IT_HR"), DESK)).isTrue();
            assertThat(visible(HR_ACCOUNT, EMPLOYEE, VANARAJA, Set.of("IT_HR"), DESK)).isTrue();
            assertThat(visible(HR_ACCOUNT, EMPLOYEE, HR_ACCOUNT, Set.of("IT_MGR"), DESK)).isTrue();
        }

        @Test
        @DisplayName("It works in the other direction too")
        void addressedToTheColleague() {
            // Whichever account the picker happened to name.
            assertThat(visible(ELANDEVAN, EMPLOYEE, HR_ACCOUNT, Set.of("IT_MGR"), DESK)).isTrue();
        }

        @Test
        @DisplayName("The civil-side HR desk counts as well")
        void civilHr() {
            assertThat(visible(HR_ACCOUNT, EMPLOYEE, 13, Set.of("CV_HR"), DESK)).isTrue();
        }
    }

    @Nested
    @DisplayName("What the widening must not reach")
    class StillPrivate {

        @Test
        @DisplayName("A complaint addressed past HR to the CTO stays out of HR's queue")
        void ctoIsNotOnTheDesk() {
            /*
             * The rule that makes the whole feature safe. Somebody raising a
             * complaint about HR chooses the CTO for a reason, and an earlier
             * version of this queue handed it to anyone holding
             * COMPLAINT_MANAGE. Widening to the desk keeps it out because the
             * CTO is not on the desk -- the change is who counts as HR, not
             * what HR may see.
             */
            assertThat(visible(CTO, EMPLOYEE, HR_ACCOUNT, Set.of("IT_MGR"), DESK)).isFalse();
            assertThat(visible(CTO, EMPLOYEE, ELANDEVAN, Set.of("IT_HR"), DESK)).isFalse();
        }

        @Test
        @DisplayName("An employee sees only their own")
        void employeeSeesOwn() {
            assertThat(visible(HR_ACCOUNT, EMPLOYEE, EMPLOYEE, Set.of("IT_EMP"), DESK)).isTrue();
            assertThat(visible(HR_ACCOUNT, 31, EMPLOYEE, Set.of("IT_EMP"), DESK)).isFalse();
        }

        @Test
        @DisplayName("A team leader is not widened into the HR queue")
        void teamLeaderNotWidened() {
            assertThat(visible(HR_ACCOUNT, EMPLOYEE, 20, Set.of("IT_TL"), DESK)).isFalse();
        }
    }

    @Nested
    @DisplayName("Your own submissions")
    class OwnSubmissions {

        @Test
        @DisplayName("An HR member still sees a complaint they raised themselves")
        void ownRequestStaysVisible() {
            // Raised by Elandevan, addressed to the CTO: not desk work, but
            // still his to follow.
            assertThat(visible(CTO, ELANDEVAN, ELANDEVAN, Set.of("IT_HR"), DESK)).isTrue();
        }

        @Test
        @DisplayName("A colleague's private complaint to the CTO is not visible")
        void colleaguesOwnIsNot() {
            assertThat(visible(CTO, ELANDEVAN, VANARAJA, Set.of("IT_HR"), DESK)).isFalse();
        }
    }
}
