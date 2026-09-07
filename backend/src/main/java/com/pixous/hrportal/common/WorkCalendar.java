package com.pixous.hrportal.common;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Which days the company works.
 *
 * <p>Saturday and Sunday are both off. Saturday used to count as a working day,
 * which meant a month was measured as twenty-six or twenty-seven days instead of
 * twenty-two or twenty-three: every Saturday somebody did not punch was recorded
 * as an absence, and on the payslip each of those absences deducted a day's pay
 * for a day nobody was asked to work.
 *
 * <p>One place, deliberately. The rule was written out three times — in the
 * attendance summary, the dashboard's attendance rate and the payroll Loss of Pay
 * — and three copies of a rule are three chances for one of them to be edited on
 * its own. When that happens the screens disagree about the same month and there
 * is nothing on any of them to say which is right.
 *
 * <p>Holidays are not handled here. They are rows in a table, they differ per
 * company, and every caller already reads them; this answers only the part that
 * is the same everywhere.
 */
public final class WorkCalendar {

    private WorkCalendar() {
    }

    /** False on Saturday and Sunday. Says nothing about holidays. */
    public static boolean isWorkingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** True on Saturday and Sunday — the inverse, for skip-this-day loops. */
    public static boolean isWeekend(LocalDate date) {
        return !isWorkingDay(date);
    }

    /**
     * Whether a month's attendance was recorded for this employee at all.
     *
     * <p>The distinction payroll has to make, and did not: a day with no
     * attendance row can mean the employee did not come in, or it can mean
     * nobody was recording attendance that month. Treating the second as the
     * first deducts a day's pay for every working day in the month, so an
     * employee on 51,000 was billed 51,000 in "absence" and the payslip came
     * out at a negative net -- silently, on a payslip that otherwise looked
     * perfectly normal.
     *
     * <p>The rule: if there is not a single attendance row for the employee in
     * the whole month, the register was not kept and nothing is deducted. One
     * row is enough to mean it was kept, and from that point a missing day is a
     * real absence and is deducted as before.
     *
     * <p>Deliberately per employee and per month, not a global setting. A
     * company that starts using attendance mid-year gets the right answer on
     * both sides of the switch without anybody configuring anything, and a
     * single employee who was never enrolled is not charged for it.
     *
     * <p>This errs towards paying. An employee genuinely absent all month with
     * no record at all is paid in full -- which is visible on the payslip and
     * can be corrected with a manual Loss of Pay figure, whereas the opposite
     * error takes somebody's whole salary and nobody notices until they ask.
     *
     * @param rowsInMonth how many attendance rows exist for that employee in
     *                    that month, of any status
     */
    public static boolean attendanceWasKept(long rowsInMonth) {
        return rowsInMonth > 0;
    }
}
