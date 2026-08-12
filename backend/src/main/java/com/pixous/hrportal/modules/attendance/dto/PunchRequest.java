package com.pixous.hrportal.modules.attendance.dto;

import java.math.BigDecimal;

/**
 * Punch-in / punch-out payload. Latitude/longitude come from the browser or
 * mobile GPS; {@code mode} = OFFICE | WFH | SITE | BIOMETRIC.
 */
public record PunchRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        String mode,
        Long siteId,
        Long officeLocationId,
        Long shiftId
) {}
