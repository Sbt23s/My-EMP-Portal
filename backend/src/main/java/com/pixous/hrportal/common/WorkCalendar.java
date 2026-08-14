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
}
