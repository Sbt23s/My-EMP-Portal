package com.pixous.hrportal.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The working-week rule, which decides a day's pay.
 *
 * <p>Worth its own test because four separate places count working days —
 * attendance, the dashboard, Loss of Pay and leave duration — and they all read
 * from here. Getting it wrong does not throw; it quietly deducts a day's salary
 * for a Saturday nobody was asked to work.
 */
class WorkCalendarTest {

    @Test
    @DisplayName("Saturday and Sunday are not working days")
    void weekendsAreNotWorked() {
        // 2026-08-14 is a Friday, so this walks one full week from a known point.
        LocalDate friday = LocalDate.of(2026, 8, 14);
        assertThat(WorkCalendar.isWorkingDay(friday)).isTrue();
        assertThat(WorkCalendar.isWorkingDay(friday.plusDays(1))).isFalse();  // Sat
        assertThat(WorkCalendar.isWorkingDay(friday.plusDays(2))).isFalse();  // Sun
        assertThat(WorkCalendar.isWorkingDay(friday.plusDays(3))).isTrue();   // Mon
    }

    @Test
    @DisplayName("isWeekend is the exact inverse")
    void inverseAgrees() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        for (int i = 0; i < 31; i++, d = d.plusDays(1)) {
            assertThat(WorkCalendar.isWeekend(d)).isNotEqualTo(WorkCalendar.isWorkingDay(d));
        }
    }

    @Test
    @DisplayName("July 2026 has 23 working days, not 27")
    void julyTwentyTwentySix() {
        // The month from the payslip that prompted this: 31 days, 8 weekend days.
        // It was being measured as 27, so a full month of absence deducted 27
        // days of pay instead of 23.
        YearMonth ym = YearMonth.of(2026, 7);
        long working = ym.atDay(1).datesUntil(ym.atEndOfMonth().plusDays(1))
                .filter(WorkCalendar::isWorkingDay)
                .count();
        assertThat(working).isEqualTo(23);
    }

    @Test
    @DisplayName("Every month has at least 18 working days and never a full month")
    void staysWithinSaneBounds() {
        // A divisor of zero would make a per-day rate infinite, and a divisor of
        // the whole month would mean the weekend rule had stopped applying.
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(2026, month);
            long working = ym.atDay(1).datesUntil(ym.atEndOfMonth().plusDays(1))
                    .filter(WorkCalendar::isWorkingDay)
                    .count();
            assertThat(working)
                    .as("working days in %s", ym)
                    .isBetween(18L, (long) ym.lengthOfMonth() - 7);
        }
    }
}
