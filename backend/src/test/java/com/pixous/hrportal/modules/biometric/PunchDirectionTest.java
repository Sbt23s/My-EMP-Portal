package com.pixous.hrportal.modules.biometric;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arriving, or leaving.
 *
 * <p>Hikvision does not reliably say which, so this rule is the portal's own
 * and every one of its decisions ends up on a payslip. Two of them are worth
 * stating outright because getting either backwards costs somebody money: an
 * arrival that moves manufactures lateness, and a departure that moves
 * backwards shortens the working day.
 */
class PunchDirectionTest {

    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 9, 8, 9, 4);
    private static final LocalDateTime MIDDAY = LocalDateTime.of(2026, 9, 8, 13, 15);
    private static final LocalDateTime EVENING = LocalDateTime.of(2026, 9, 8, 18, 32);

    @Nested
    @DisplayName("The terminal's own answer is used when it gives one")
    class TerminalSaysSo {

        @Test
        @DisplayName("attendanceStatus 1 is an arrival, whatever is already recorded")
        void onWork() {
            // Honoured even against a recorded arrival: using our fallback in
            // the face of a real answer would be inventing a disagreement.
            assertThat(PunchDirection.decide(1, null, null)).isEqualTo(PunchDirection.IN);
            assertThat(PunchDirection.decide(1, MORNING, null)).isEqualTo(PunchDirection.IN);
        }

        @Test
        @DisplayName("attendanceStatus 2 is a departure, even as the first punch of the day")
        void offWork() {
            /*
             * Somebody whose morning punch was missed entirely -- the device was
             * offline, or they came in through another door. The terminal says
             * they are leaving, so this is a departure, and the day is left with
             * a missing punch-in rather than a fabricated one.
             */
            assertThat(PunchDirection.decide(2, null, null)).isEqualTo(PunchDirection.OUT);
        }

        @Test
        @DisplayName("Break and overtime markers fall through to the fallback")
        void breakAndOvertimeMarkers() {
            /*
             * attendanceStatus 3-6 are break and overtime boundaries. They are
             * neither an arrival nor a departure, and forcing one into either
             * would write a real punch time into the wrong field -- a break at
             * eleven becoming the departure, and the working day ending at
             * eleven.
             */
            assertThat(PunchDirection.decide(3, MORNING, null)).isEqualTo(PunchDirection.OUT);
            assertThat(PunchDirection.decide(3, null, null)).isEqualTo(PunchDirection.IN);
            assertThat(PunchDirection.decide(5, null, null)).isEqualTo(PunchDirection.IN);
        }
    }

    @Nested
    @DisplayName("When the terminal says nothing, which is most of the time")
    class Fallback {

        @Test
        @DisplayName("attendanceStatus 0 -- undefined -- is not read as a direction")
        void undefinedIsNotADirection() {
            /*
             * 0 is what Hikvision's own worked example carries for a successful
             * face punch. Reading it as a code rather than as "no answer" is
             * the mistake this pins.
             */
            assertThat(PunchDirection.decide(0, null, null)).isEqualTo(PunchDirection.IN);
            assertThat(PunchDirection.decide(0, MORNING, null)).isEqualTo(PunchDirection.OUT);
        }

        @Test
        @DisplayName("A missing attendanceStatus behaves the same as 0")
        void nullStatus() {
            assertThat(PunchDirection.decide(null, null, null)).isEqualTo(PunchDirection.IN);
            assertThat(PunchDirection.decide(null, MORNING, null)).isEqualTo(PunchDirection.OUT);
        }

        @Test
        @DisplayName("The first punch of the day is the arrival")
        void firstIsIn() {
            assertThat(PunchDirection.decide(null, null, null)).isEqualTo(PunchDirection.IN);
        }

        @Test
        @DisplayName("Every punch after the arrival is the departure")
        void laterIsOut() {
            assertThat(PunchDirection.decide(null, MORNING, null)).isEqualTo(PunchDirection.OUT);
            assertThat(PunchDirection.decide(null, MORNING, MIDDAY)).isEqualTo(PunchDirection.OUT);
        }
    }

    @Nested
    @DisplayName("An arrival does not move")
    class ArrivalIsFixed {

        @Test
        @DisplayName("The first arrival is recorded")
        void firstArrivalWrites() {
            assertThat(PunchDirection.IN.shouldReplace(null, MORNING)).isTrue();
        }

        @Test
        @DisplayName("A later punch never overwrites the recorded arrival")
        void arrivalNeverOverwritten() {
            /*
             * The failure this prevents. Somebody arrives at 09:04, steps out
             * for a delivery and comes back at 11:00. If that second punch
             * replaced the arrival, the register would say they got in at
             * eleven -- nearly two hours of lateness that never happened, and
             * a deduction with it.
             */
            LocalDateTime laterSameDay = LocalDateTime.of(2026, 9, 8, 11, 0);
            assertThat(PunchDirection.IN.shouldReplace(MORNING, laterSameDay)).isFalse();
        }

        @Test
        @DisplayName("Not even an earlier punch moves it")
        void earlierDoesNotMoveArrivalEither() {
            // Deliberately not "keep the earliest". A punch that arrives out of
            // order is usually a device replaying its buffer, and the recorded
            // arrival is the one the day was already judged on.
            LocalDateTime earlier = LocalDateTime.of(2026, 9, 8, 8, 30);
            assertThat(PunchDirection.IN.shouldReplace(MORNING, earlier)).isFalse();
        }
    }

    @Nested
    @DisplayName("A departure moves forwards, and only forwards")
    class DepartureExtends {

        @Test
        @DisplayName("The first departure is recorded")
        void firstDepartureWrites() {
            assertThat(PunchDirection.OUT.shouldReplace(null, MIDDAY)).isTrue();
        }

        @Test
        @DisplayName("A later departure replaces an earlier one")
        void laterDepartureWins() {
            // Somebody who punched out at lunchtime and again at half past six
            // left at half past six.
            assertThat(PunchDirection.OUT.shouldReplace(MIDDAY, EVENING)).isTrue();
        }

        @Test
        @DisplayName("An earlier departure does not pull the day back")
        void earlierDepartureIgnored() {
            /*
             * The out-of-order case, which is not hypothetical: a device that
             * has been offline replays its buffer hours later, so a lunchtime
             * punch can arrive after an evening one. Letting it win would cut
             * five hours off a day that was already worked.
             */
            assertThat(PunchDirection.OUT.shouldReplace(EVENING, MIDDAY)).isFalse();
        }

        @Test
        @DisplayName("The identical time is not a change")
        void sameTimeIsNoChange() {
            // A redelivery that got past deduplication should not count as work.
            assertThat(PunchDirection.OUT.shouldReplace(EVENING, EVENING)).isFalse();
        }
    }
}
