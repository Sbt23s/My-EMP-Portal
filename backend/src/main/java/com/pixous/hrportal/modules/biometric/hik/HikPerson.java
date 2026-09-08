package com.pixous.hrportal.modules.biometric.hik;

/**
 * One person as Hikvision holds them, reduced to the fields this portal uses.
 *
 * <p>From {@code PersonInfo(1)} (§A.3.125) on the {@code /persons/list}
 * response. {@code personCode} is Hikvision's "Employee No." and is the field
 * that joins a terminal identity to an employee here; {@code personId} is the
 * only one of the two that appears on a pushed authentication event.
 *
 * @param personId   Hikvision's identifier, and the join key for events
 * @param personCode the Employee No., which may be unset on the Hikvision side
 * @param firstName  as recorded on the terminal
 * @param lastName   as recorded on the terminal
 */
public record HikPerson(String personId, String personCode,
                        String firstName, String lastName) {

    /** A readable name for a log line or an unmatched-punch screen. */
    public String displayName() {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String joined = (first + " " + last).trim();
        return joined.isEmpty() ? "(unnamed)" : joined;
    }
}
