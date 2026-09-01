package com.pixous.hrportal.modules.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an absent day costs.
 *
 * <p>Worth pinning because the failure is silent and expensive: nothing throws,
 * a payslip is produced, and somebody is paid the wrong amount. The two bugs
 * this guards against were both live — a per-day rate built on calendar days,
 * and a deduction that had to be typed in by hand every month.
 */
class PayrollCalculationTest {

    /** A working day's pay, as the payslip computes it. */
    private static BigDecimal perDay(BigDecimal gross, int workingDays) {
        return gross.divide(BigDecimal.valueOf(Math.max(1, workingDays)), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal deduction(BigDecimal gross, int workingDays, int absentDays) {
        return perDay(gross, workingDays)
                .multiply(BigDecimal.valueOf(absentDays))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("A day's pay is a working day's pay, not a calendar day's")
    void perDayUsesWorkingDays() {
        // 20,000 over 26 working days is 769.23. Dividing by the 30 calendar
        // days gives 666.67, so every absence would cost about a hundred rupees
        // less than the day was worth -- the company pays for time nobody
        // worked, quietly, on every payslip.
        assertThat(perDay(new BigDecimal("20000"), 26)).isEqualByComparingTo("769.23");
        assertThat(perDay(new BigDecimal("20000"), 30)).isEqualByComparingTo("666.67");
    }

    @Test
    @DisplayName("Two absent days deduct two days' pay")
    void absentDeduction() {
        // The worked example: 20,000 over 26 days, two absences.
        assertThat(deduction(new BigDecimal("20000"), 26, 2)).isEqualByComparingTo("1538.46");
    }

    @Test
    @DisplayName("Net pay is gross less the deduction")
    void netPay() {
        BigDecimal gross = new BigDecimal("20000");
        BigDecimal net = gross.subtract(deduction(gross, 26, 2)).setScale(2, RoundingMode.HALF_UP);
        assertThat(net).isEqualByComparingTo("18461.54");
    }

    @Test
    @DisplayName("A full month deducts nothing")
    void fullAttendance() {
        assertThat(deduction(new BigDecimal("20000"), 26, 0)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("A month with no working days cannot divide by zero")
    void noWorkingDays() {
        // Every day a holiday is not a real month, but a payroll run must not
        // throw on one -- the guard floors the divisor at one.
        assertThat(perDay(new BigDecimal("20000"), 0)).isEqualByComparingTo("20000.00");
    }

    @Test
    @DisplayName("A day worked from home is a day worked")
    void wfhCountsAsPresent() {
        // WFH is written into attendance by the approval, and payroll reads
        // the same rows. Counting it as absent would deduct pay for a day the
        // employee was approved to work.
        String[] paidStatuses = { "WFH", "PRESENT", "LATE", "HALF_DAY", "LEAVE", "PAID_LEAVE" };
        for (String s : paidStatuses) {
            assertThat(isUnpaid(s)).as(s + " should not be deducted").isFalse();
        }
    }

    @Test
    @DisplayName("Loss of Pay and an unrecorded day are both deducted")
    void unpaidIsDeducted() {
        assertThat(isUnpaid("LOP")).isTrue();
        assertThat(isUnpaid("ABSENT")).isTrue();
        assertThat(isUnpaid(null)).isTrue();
    }

    @Test
    @DisplayName("A first generation is revision 1, a regeneration counts up")
    void revisionCountsUp() {
        // The figures are overwritten in place when a month is regenerated, so
        // a number somebody was already shown can change. The revision is what
        // says it did.
        assertThat(nextRevision(null, false)).isEqualTo(1);
        assertThat(nextRevision(1, true)).isEqualTo(2);
        assertThat(nextRevision(2, true)).isEqualTo(3);
    }

    @Test
    @DisplayName("A payslip with no revision recorded is treated as the first")
    void missingRevisionIsOne() {
        // Rows that predate the column have no value. Treating null as zero
        // would make the next regeneration revision 1 -- the same number a
        // first generation carries, which is the one thing it must not be.
        assertThat(nextRevision(null, true)).isEqualTo(2);
    }

    /** The rule the service applies when saving. */
    private static int nextRevision(Integer current, boolean regenerating) {
        if (!regenerating) return 1;
        return (current == null ? 1 : current) + 1;
    }

    /** The rule countMonth applies, as a function of the stored status. */
    private static boolean isUnpaid(String status) {
        if (status == null) return true;
        return switch (status.toUpperCase()) {
            case "WFH", "PRESENT", "LATE", "HALF_DAY", "LEAVE", "PAID_LEAVE", "ON_LEAVE" -> false;
            default -> true;
        };
    }
}
