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
 * arrival that drifts later manufactures lateness, and a departure that moves
 * backwards shortens the working day.
 */
class PunchDirectionTest {

    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 9, 8, 8, 30);
    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 9, 8, 9, 4);
    private static final LocalDateTime MIDDAY = LocalDateTime.of(2026, 9, 8, 13, 15);
    private static final LocalDateTime EVENING = LocalDateTime.of(2026, 9, 8, 18, 32);

    @Nested
    @DisplayName("The terminal's attendanceStatus is not consulted")
    class StatusIgnored {

        @Test
        @DisplayName("A status-2 morning punch is still the arrival")
        void morningStatusTwoIsStillArrival() {
            /*
             * The bug this pins, from real exported data. On 07 September the
             * terminal reported "off work" on people walking in at ten to nine,
             * so the morning punch was filed as a departure and the evening one
             * that carried "on work" became the arrival:
             *
             *   PIX-E025   07 Sep IN 6:48 PM   07 Sep OUT 8:48 AM
             *
             * Nobody arrived at a quarter to seven in the evening. The
             * terminal's rule configuration is not an observation; the
             * timestamps are, and they are never wrong about their own order.
             */
            assertThat(PunchDirection.decide(2, null, null, MORNING))
                    .isEqualTo(PunchDirection.IN);
        }

        @Test
        @DisplayName("A status-1 evening punch is still the departure")
        void eveningStatusOneIsStillDeparture() {
            assertThat(PunchDirection.decide(1, MORNING, null, EVENING))
                    .isEqualTo(PunchDirection.OUT);
        }

        @Test
        @DisplayName("Every status value gives the same answer as no status at all")
        void statusMakesNoDifference() {
            for (Integer status : new Integer[] { null, 0, 1, 2, 3, 4, 5, 6 }) {
                assertThat(PunchDirection.decide(status, null, null, MORNING))
                        .as("first punch, status %s", status)
                        .isEqualTo(PunchDirection.IN);
                assertThat(PunchDirection.decide(status, MORNING, null, EVENING))
                        .as("later punch, status %s", status)
                        .isEqualTo(PunchDirection.OUT);
            }
        }
    }

    @Nested
    @DisplayName("The timeline decides")
    class Timeline {

        @Test
        @DisplayName("The first punch of the day is the arrival")
        void firstIsIn() {
            assertThat(PunchDirection.decide(null, null, null, MORNING))
                    .isEqualTo(PunchDirection.IN);
        }

        @Test
        @DisplayName("Every punch after the arrival is the departure")
        void laterIsOut() {
            assertThat(PunchDirection.decide(null, MORNING, null, MIDDAY))
                    .isEqualTo(PunchDirection.OUT);
            assertThat(PunchDirection.decide(null, MORNING, MIDDAY, EVENING))
                    .isEqualTo(PunchDirection.OUT);
        }

        @Test
        @DisplayName("A punch earlier than the recorded arrival is the real arrival")
        void earlierPunchIsTheArrival() {
            /*
             * What makes replay safe. A device that has been offline can hand
             * over the evening punch first, and the morning one arrives after
             * it. Whichever order they come in, the earliest must end up in
             * punch-in -- otherwise the register says somebody arrived at half
             * past six in the evening.
             */
            assertThat(PunchDirection.decide(null, MORNING, null, EARLY))
                    .isEqualTo(PunchDirection.IN);
        }
    }

    @Nested
    @DisplayName("An arrival moves backwards, and only backwards")
    class ArrivalMovesEarlier {

        @Test
        @DisplayName("The first arrival is recorded")
        void firstArrivalWrites() {
            assertThat(PunchDirection.IN.shouldReplace(null, MORNING)).isTrue();
        }

        @Test
        @DisplayName("A later punch never overwrites the recorded arrival")
        void arrivalNeverPushedLater() {
            /*
             * Somebody arrives at 09:04, steps out for a delivery and comes back
             * at 11:00. If that second punch replaced the arrival, the register
             * would say they got in at eleven -- nearly two hours of lateness
             * that never happened, and a deduction with it.
             */
            LocalDateTime laterSameDay = LocalDateTime.of(2026, 9, 8, 11, 0);
            assertThat(PunchDirection.IN.shouldReplace(MORNING, laterSameDay)).isFalse();
        }

        @Test
        @DisplayName("An earlier punch corrects it")
        void earlierPunchCorrectsTheArrival() {
            // The replay case: the real 08:30 arrival turning up after the
            // 09:04 one was already written.
            assertThat(PunchDirection.IN.shouldReplace(MORNING, EARLY)).isTrue();
        }

        @Test
        @DisplayName("The identical time is not a change")
        void sameArrivalIsNoChange() {
            assertThat(PunchDirection.IN.shouldReplace(MORNING, MORNING)).isFalse();
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

    @Nested
    @DisplayName("A punch with no time changes nothing")
    class NoTime {

        @Test
        void neitherDirectionWrites() {
            assertThat(PunchDirection.IN.shouldReplace(null, null)).isFalse();
            assertThat(PunchDirection.OUT.shouldReplace(EVENING, null)).isFalse();
        }
    }
}
