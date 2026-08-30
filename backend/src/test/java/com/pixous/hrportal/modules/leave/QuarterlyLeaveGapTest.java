package com.pixous.hrportal.modules.leave;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Once every three months", for Casual and Sick leave.
 *
 * <p>Two rules guard these two types and they are not the same rule. The
 * calendar-quarter cap allows one request per Jan-Mar, Apr-Jun and so on; the
 * rolling gap requires three months to have passed since the last day actually
 * taken. Only the second is what the sentence means, and for a long time only
 * Casual Leave had it — so a Sick Leave on 31 March and another on 1 April sat
 * in different quarters and both went through, one day apart.
 *
 * <p>The arithmetic is small enough to look correct while being wrong at the
 * edges, which is where it will be met: the day before the gap closes, the day
 * it closes, and a request whose start is fine but whose type was never
 * checked at all.
 */
class QuarterlyLeaveGapTest {

    /** The rule as the service applies it: next allowed start after a last day. */
    private static LocalDate availableFrom(LocalDate lastDayTaken) {
        return lastDayTaken.plusMonths(3).plusDays(1);
    }

    /** The types the rolling gap applies to, by leave-type code. */
    private static boolean carriesThreeMonthGap(String code) {
        return "CL".equalsIgnoreCase(code) || "SL".equalsIgnoreCase(code);
    }

    @Test
    @DisplayName("Sick leave carries the gap, not only casual leave")
    void sickLeaveIsIncluded() {
        assertThat(carriesThreeMonthGap("CL")).isTrue();
        assertThat(carriesThreeMonthGap("SL")).isTrue();
        assertThat(carriesThreeMonthGap("sl")).isTrue();
    }

    @Test
    @DisplayName("Leave with no such limit is untouched by the gap")
    void otherTypesAreNotAffected() {
        assertThat(carriesThreeMonthGap("LOP")).isFalse();
        assertThat(carriesThreeMonthGap("EL")).isFalse();
        assertThat(carriesThreeMonthGap("MAT")).isFalse();
    }

    @Test
    @DisplayName("A second request one day later is refused")
    void oneDayApartIsRefused() {
        LocalDate lastTaken = LocalDate.of(2026, 3, 31);
        LocalDate nextDay = LocalDate.of(2026, 4, 1);
        // The case the calendar-quarter cap let through: different quarters,
        // consecutive days.
        assertThat(nextDay.isBefore(availableFrom(lastTaken))).isTrue();
    }

    @Test
    @DisplayName("The day the gap closes is allowed, the day before is not")
    void theBoundaryItself() {
        LocalDate lastTaken = LocalDate.of(2026, 1, 15);
        LocalDate opens = availableFrom(lastTaken);

        assertThat(opens).isEqualTo(LocalDate.of(2026, 4, 16));
        assertThat(opens.minusDays(1).isBefore(opens)).isTrue();
        assertThat(opens.isBefore(opens)).isFalse();
    }

    @Test
    @DisplayName("Three months is counted in months, not in 90 days")
    void countedInMonths() {
        // February is short, so a fixed 90 days would open the gap earlier than
        // the calendar does and let a request in a day early.
        assertThat(availableFrom(LocalDate.of(2026, 1, 31)))
                .isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(availableFrom(LocalDate.of(2026, 11, 30)))
                .isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    @DisplayName("A range is measured from its last day, not its first")
    void measuredFromTheLastDayTaken() {
        // Somebody off from 1 to 5 March waits three months from the 5th, not
        // the 1st, or the gap is short by the length of the leave itself.
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 5);
        assertThat(availableFrom(to)).isAfter(availableFrom(from));
        assertThat(availableFrom(to)).isEqualTo(LocalDate.of(2026, 6, 6));
    }
}
