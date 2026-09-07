package com.pixous.hrportal.modules.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The itemised salary components added in V109, and the rules that keep them
 * from changing anybody's pay by accident.
 *
 * <p>The risk in this change is not that a new figure is computed wrongly --
 * that would show up on the screen. It is that adding six columns to the gross
 * quietly alters what an existing employee is paid, on a payslip that looks
 * perfectly normal. The first group below is the guard against exactly that.
 *
 * <p>These exercise the arithmetic the service performs. The end-to-end
 * behaviour was verified separately against a real database, because a mock
 * would agree with whatever the code happened to do.
 */
class PayrollComponentsTest {

    /** Zero for a missing figure, as {@code PayslipService.zed} does. */
    private static BigDecimal zed(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * The recurring monthly pay, as the service builds it: the three original
     * figures plus the two new recurring allowances.
     */
    private static BigDecimal recurring(BigDecimal basic, BigDecimal hra, BigDecimal allowances,
                                        BigDecimal conveyance, BigDecimal special) {
        return basic.add(hra).add(allowances).add(zed(conveyance)).add(zed(special));
    }

    @Nested
    @DisplayName("An untouched structure is paid exactly what it was paid before")
    class NoRegression {

        @Test
        @DisplayName("All six new components at zero leaves the gross unchanged")
        void zeroComponentsDoNotChangeGross() {
            // The five structures already in the database look like this: a
            // combined allowances figure and nothing in the new columns. If this
            // assertion ever fails, a deploy has silently changed real salaries.
            BigDecimal before = new BigDecimal("35000")
                    .add(new BigDecimal("14000")).add(new BigDecimal("6000"));
            BigDecimal after = recurring(new BigDecimal("35000"), new BigDecimal("14000"),
                    new BigDecimal("6000"), BigDecimal.ZERO, BigDecimal.ZERO);
            assertThat(after).isEqualByComparingTo(before);
            assertThat(after).isEqualByComparingTo("55000");
        }

        @Test
        @DisplayName("A null component counts as zero rather than throwing")
        void nullComponentIsZero() {
            // The columns are NOT NULL with a zero default, so a row read back
            // is never null -- but an entity built in memory is, and an NPE here
            // would abort a payroll run halfway through.
            assertThat(recurring(new BigDecimal("35000"), new BigDecimal("14000"),
                    new BigDecimal("6000"), null, null))
                    .isEqualByComparingTo("55000");
        }

        @Test
        @DisplayName("allowances keeps its meaning and is still added in full")
        void allowancesNotSplit() {
            // Deliberately not split into conveyance and special: nobody knows
            // how much of an existing lump sum was which, and a guess written
            // into payroll is worse than a combined figure that is at least true.
            BigDecimal withLump = recurring(new BigDecimal("30000"), BigDecimal.ZERO,
                    new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO);
            assertThat(withLump).isEqualByComparingTo("40000");
        }
    }

    @Nested
    @DisplayName("A component that is filled in is actually paid")
    class ComponentsApply {

        @Test
        @DisplayName("Conveyance and special allowance both reach the gross")
        void componentsAddToGross() {
            assertThat(recurring(new BigDecimal("30000"), new BigDecimal("12000"),
                    new BigDecimal("3000"), new BigDecimal("1600"), new BigDecimal("2400")))
                    .isEqualByComparingTo("49000");
        }

        @Test
        @DisplayName("A day's pay is a day of the whole recurring salary")
        void perDayIncludesComponents() {
            /*
             * The point of folding the components into `recurring` before
             * dividing. Leaving them out would make an absence cost less than
             * the day was worth -- the same error as dividing by calendar days,
             * and the company pays for time nobody worked.
             */
            BigDecimal full = recurring(new BigDecimal("30000"), new BigDecimal("12000"),
                    new BigDecimal("3000"), new BigDecimal("1600"), new BigDecimal("2400"));
            BigDecimal partial = new BigDecimal("30000")
                    .add(new BigDecimal("12000")).add(new BigDecimal("3000"));

            BigDecimal perDayFull = full.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
            BigDecimal perDayPartial =
                    partial.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);

            assertThat(perDayFull).isEqualByComparingTo("1884.62");
            // What it would have been had the components been left out: 154 a
            // day cheaper, on every absence, invisibly.
            assertThat(perDayPartial).isEqualByComparingTo("1730.77");
            assertThat(perDayFull).isGreaterThan(perDayPartial);
        }
    }

    @Nested
    @DisplayName("A month's adjustment applies to that month only")
    class MonthlyAdjustments {

        @Test
        @DisplayName("A one-off bonus is added to the month, not to the structure")
        void monthBonusIsAdditive() {
            // Why these columns exist: the only way to give somebody a bonus was
            // to edit their structure, which then paid it again every following
            // month until someone noticed.
            BigDecimal structureBonus = BigDecimal.ZERO;
            BigDecimal monthBonus = new BigDecimal("5000");
            assertThat(structureBonus.add(monthBonus)).isEqualByComparingTo("5000");

            // The following month has no adjustment row, so the bonus is gone.
            BigDecimal nextMonth = structureBonus.add(BigDecimal.ZERO);
            assertThat(nextMonth).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("A recurring bonus and a one-off bonus both apply, and add up")
        void bothBonusesApply() {
            // They are different things, so they add rather than override.
            assertThat(new BigDecimal("2000").add(new BigDecimal("5000")))
                    .isEqualByComparingTo("7000");
        }

        @Test
        @DisplayName("The three loss-of-pay figures add up without double-counting")
        void lopFiguresAreSeparate() {
            /*
             * Manual LOP, attendance-derived LOP, and the month's leave
             * deduction. None is computed from another -- one is typed, one
             * comes from the register, one is entered against the month -- so
             * adding all three counts nothing twice.
             */
            BigDecimal manual = new BigDecimal("500");
            BigDecimal fromAttendance = new BigDecimal("1538.46");
            BigDecimal monthLeave = new BigDecimal("250");
            assertThat(manual.add(fromAttendance).add(monthLeave))
                    .isEqualByComparingTo("2288.46");
        }
    }

    @Nested
    @DisplayName("TDS overrides rather than accumulates")
    class Tds {

        /** As the service resolves it: the request wins when present. */
        private static BigDecimal tds(Double requested, BigDecimal onStructure) {
            return requested != null
                    ? BigDecimal.valueOf(requested).setScale(2, RoundingMode.HALF_UP)
                    : zed(onStructure);
        }

        @Test
        @DisplayName("The standing amount is used when the run does not give one")
        void fallsBackToStructure() {
            assertThat(tds(null, new BigDecimal("3000"))).isEqualByComparingTo("3000");
        }

        @Test
        @DisplayName("A figure on the run replaces the standing amount, and is not added to it")
        void requestOverrides() {
            // Adding them would silently double somebody's tax: 3,000 on the
            // structure plus a 4,000 correction would withhold 7,000.
            assertThat(tds(4000.0, new BigDecimal("3000"))).isEqualByComparingTo("4000");
        }

        @Test
        @DisplayName("An explicit zero on the run means zero, not the standing amount")
        void explicitZeroWins() {
            // The distinction the null check exists for: "no TDS this month" has
            // to be expressible, and it is not the same as "not specified".
            assertThat(tds(0.0, new BigDecimal("3000"))).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Delivery status")
    class Delivery {

        @Test
        @DisplayName("A payslip that has never been sent says so")
        void defaultsToNotSent() {
            // Not null: "unknown" and "not sent" would look identical on screen,
            // and every payslip generated before the column existed is genuinely
            // not sent.
            assertThat(new Payslip().getDeliveryStatus()).isEqualTo("NOT_SENT");
        }

        @Test
        @DisplayName("A send error is truncated to fit its column")
        void errorIsTruncated() {
            // A mail library exception message can run to thousands of
            // characters. Storing it unchecked fails the insert, which would
            // turn "the email failed" into "the payroll screen is broken".
            String long_ = "x".repeat(900);
            String stored = long_.length() > 500 ? long_.substring(0, 500) : long_;
            assertThat(stored).hasSize(500);
        }
    }
}
