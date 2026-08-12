package com.pixous.hrportal.modules.calendar;

import java.time.LocalDate;

public class CalendarDTOs {

    /**
     * One thing on one day, whatever kind of thing it is. Birthdays and work
     * anniversaries are worked out from the employee record rather than stored,
     * so they carry a person; a meeting carries a time and a place instead.
     * Everything the calendar draws comes through this one shape.
     */
    public record CalendarEvent(
            /** Null for a birthday or anniversary — those have no row of their own. */
            Long id,
            /** BIRTHDAY | ANNIVERSARY | CELEBRATION | MEETING | TRAINING | OTHER */
            String type,
            String title,
            String description,
            LocalDate date,
            /** Set only for something running over more than one day. */
            LocalDate endDate,
            String startTime,
            String endTime,
            String location,
            /** Null means the whole company. */
            String audienceTeam,
            // ---- set only for a birthday or an anniversary ----
            Long userId,
            String employeeName,
            String employeeCode,
            String team,
            String photoPath,
            /** Years completed, on an anniversary. */
            Integer years
    ) {}

    /** Create or update payload for a company event. */
    public record EventRequest(
            String title,
            String description,
            String eventType,
            LocalDate eventDate,
            LocalDate endDate,
            String startTime,
            String endTime,
            String location,
            String audienceTeam
    ) {}
}
