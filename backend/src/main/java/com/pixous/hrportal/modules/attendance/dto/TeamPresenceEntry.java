package com.pixous.hrportal.modules.attendance.dto;

import java.time.LocalDateTime;

/** Whether one teammate has punched in today — drives the Teams page status. */
public record TeamPresenceEntry(
        Long userId,
        boolean punchedIn,
        LocalDateTime punchInAt
) {}
