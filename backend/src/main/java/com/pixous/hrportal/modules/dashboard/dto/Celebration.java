package com.pixous.hrportal.modules.dashboard.dto;

import java.time.LocalDate;

/** An upcoming birthday or work anniversary for the team celebrations widget. */
public record Celebration(
        Long userId,
        String name,
        String employeeCode,
        String team,
        String photoPath,
        String type,        // BIRTHDAY | ANNIVERSARY
        LocalDate date,     // this year's occurrence
        int daysUntil,      // 0 = today
        Integer years       // years completed (anniversary only)
) {}
