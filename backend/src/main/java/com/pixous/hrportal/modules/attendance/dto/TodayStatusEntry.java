package com.pixous.hrportal.modules.attendance.dto;

/** One row for the "who's absent / on leave today" dashboard widget. */
public record TodayStatusEntry(
        Long userId,
        String name,
        String employeeCode,
        String team
) {}
