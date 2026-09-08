package com.pixous.hrportal.modules.attendance.dto;

public record AttendanceSummary(
        int month,
        int year,
        long presentDays,
        long wfhDays,
        long lateDays,
        long absentDays,
        int totalOvertimeMinutes,
        /** Minutes lost to late arrivals across the month. */
        int totalLateMinutes,
        /** Working days counted so far — Sundays and holidays excluded. */
        int workingDays,

        /**
         * Minutes actually worked across the month.
         *
         * <p>Summed from the days that have both a punch-in and a punch-out. A
         * day still open contributes nothing: the hours are not known until
         * somebody leaves, and counting a partial day would make every morning
         * look like a short one.
         */
        int totalWorkedMinutes,

        /**
         * Present days as a percentage of the working days counted so far.
         *
         * <p>Computed here rather than in the browser so the page and the
         * export cannot disagree — and because the denominator is not obvious:
         * it is working days elapsed, not days in the month, so somebody
         * perfect on the 8th reads 100% rather than 27%.
         */
        int attendancePercent,

        /**
         * Days in the month that carried an approved permission.
         *
         * <p>Counted separately from the hours because they answer different
         * questions: four fifteen-minute permissions and one four-hour one are
         * not the same month, and neither figure implies the other.
         *
         * <p>Approved only. A pending request is a question nobody has
         * answered, and counting it would show time off that has not been
         * granted — the same rule the punch-out window already applies.
         */
        int permissionDays,

        /**
         * Hours of approved permission across the month, to one decimal.
         *
         * <p>Held as a double rather than minutes because that is the unit the
         * request itself is made in — somebody asks for an hour and a half, not
         * for ninety minutes — and rounding it into minutes here would make the
         * total disagree with the sum of the requests behind it.
         */
        double permissionHours
) {}
