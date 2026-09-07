package com.pixous.hrportal.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Absent, versus never recorded.
 *
 * <p>Payroll read a day with no attendance row as an absence. That is right
 * when the register is being kept and catastrophic when it is not: every
 * working day in the month lands in the same branch, and the whole salary is
 * deducted. Nothing throws, a payslip is produced, and the net comes out
 * negative on figures that otherwise look perfectly normal.
 *
 * <p>Observed on a copy of the real database before the fix: gross 55,000,
 * deductions 55,000.05, net -212.05.
 */
class AttendanceTrackedTest {

    @Nested
    @DisplayName("Whether the register was kept")
    class WasKept {

        @Test
        @DisplayName("No rows at all means the register was not kept")
        void noRows() {
            assertThat(WorkCalendar.attendanceWasKept(0)).isFalse();
        }

        @Test
        @DisplayName("A single row is enough to mean it was kept")
        void oneRow() {
            // Deliberately one, not a threshold. A company that records
            // attendance patchily is still recording it, and guessing at a
            // minimum would silently forgive real absences.
            assertThat(WorkCalendar.attendanceWasKept(1)).isTrue();
        }

        @Test
        @DisplayName("A full month of rows means it was kept")
        void manyRows() {
            assertThat(WorkCalendar.attendanceWasKept(21)).isTrue();
        }
    }

    @Nested
    @DisplayName("What that does to the money")
    class Money {

        /** A working day's pay, as the payslip computes it. */
        private static BigDecimal perDay(BigDecimal recurring, int workingDays) {
            return recurring.divide(BigDecimal.valueOf(Math.max(1, workingDays)),
                    2, RoundingMode.HALF_UP);
        }

        /**
         * The absence deduction, with the rule applied.
         *
         * <p>Mirrors the branch in {@code countMonth}: a day with no row is
         * only counted when the register was kept.
         */
        private static BigDecimal absenceDeduction(BigDecimal recurring, int workingDays,
                                                   int daysWithNoRow, long rowsInMonth) {
            int unpaid = WorkCalendar.attendanceWasKept(rowsInMonth) ? daysWithNoRow : 0;
            return perDay(recurring, workingDays)
                    .multiply(BigDecimal.valueOf(unpaid))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        @Test
        @DisplayName("An untracked month deducts nothing, however many days are missing")
        void untrackedMonthDeductsNothing() {
            // The live failure: 21 working days, not one attendance row, and
            // 55,000 of "absence" against a 55,000 salary.
            BigDecimal recurring = new BigDecimal("55000");
            assertThat(absenceDeduction(recurring, 21, 21, 0))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Net pay stays positive where it used to go negative")
        void netIsNoLongerNegative() {
            BigDecimal gross = new BigDecimal("55000");
            BigDecimal statutory = new BigDecimal("212");   // PF + PT
            BigDecimal absence = absenceDeduction(gross, 21, 21, 0);
            BigDecimal net = gross.subtract(statutory).subtract(absence);

            assertThat(net).isEqualByComparingTo("54788");
            assertThat(net).isPositive();
        }

        @Test
        @DisplayName("A tracked month still deducts real absences")
        void trackedMonthStillDeducts() {
            /*
             * The half of the rule that must not be lost. Nineteen days
             * recorded and two missing, in a month the register was plainly
             * kept, is two days of absence and is deducted exactly as before.
             */
            BigDecimal recurring = new BigDecimal("55000");
            BigDecimal deduction = absenceDeduction(recurring, 21, 2, 19);
            // 55000 / 21 = 2619.05 a day, twice.
            assertThat(deduction).isEqualByComparingTo("5238.10");
        }

        @Test
        @DisplayName("One recorded day is enough to make the rest of the month deductible")
        void oneRowMakesTheMonthTracked() {
            // The boundary, stated as money: a single punch means the register
            // exists, so the other twenty days are absences and are charged.
            BigDecimal deduction = absenceDeduction(new BigDecimal("55000"), 21, 20, 1);
            assertThat(deduction).isEqualByComparingTo("52381.00");
            assertThat(deduction).isPositive();
        }

        @Test
        @DisplayName("The error is towards paying, not towards withholding")
        void errsTowardsPaying() {
            /*
             * Stated on purpose, because it is a real trade-off rather than an
             * oversight. Somebody genuinely absent all month with no record at
             * all is now paid in full. That is visible on the payslip and can
             * be corrected with a manual Loss of Pay figure; the opposite error
             * takes a whole salary and nobody notices until the employee asks.
             */
            BigDecimal untracked = absenceDeduction(new BigDecimal("55000"), 21, 21, 0);
            BigDecimal tracked = absenceDeduction(new BigDecimal("55000"), 21, 21, 21);
            assertThat(untracked).isEqualByComparingTo("0.00");
            assertThat(tracked).isGreaterThan(untracked);
        }
    }
}
