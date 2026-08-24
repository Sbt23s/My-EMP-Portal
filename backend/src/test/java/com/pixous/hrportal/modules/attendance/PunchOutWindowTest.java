package com.pixous.hrportal.modules.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When somebody is allowed to punch out.
 *
 * <p>This rule decides whether the company can clock off. If it is wrong in
 * the strict direction nobody can end their day; if it is wrong in the loose
 * direction the attendance record stops meaning anything. Both are worth a
 * test that does not need a database.
 */
class PunchOutWindowTest {

    private static final LocalTime OFFICE_END = LocalTime.of(18, 0);

    @Test
    @DisplayName("with no permission, the day ends when the office does")
    void noPermission() {
        assertThat(AttendanceService.earliestPunchOut(OFFICE_END, List.of()))
                .isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("an approved permission moves the line to when it starts")
    void approvedPermissionLetsThemLeaveEarly() {
        assertThat(AttendanceService.earliestPunchOut(OFFICE_END, List.of("15:00")))
                .isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    @DisplayName("with two permissions, the earlier one wins")
    void earliestOfSeveralWins() {
        assertThat(AttendanceService.earliestPunchOut(OFFICE_END, List.of("16:30", "14:00", "17:00")))
                .isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("a permission that starts after the office closes does not extend the day")
    void latePermissionDoesNotPushTheLineOut() {
        // Somebody with permission from 19:00 can still punch out at 18:00 --
        // the rule is a floor on leaving early, not a curfew.
        assertThat(AttendanceService.earliestPunchOut(OFFICE_END, List.of("19:00")))
                .isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("a malformed time is ignored rather than read as midnight")
    void rubbishTimesAreIgnored() {
        // The dangerous failure: parsing "" or "half three" as 00:00 would
        // hand out permission to leave at the start of the day.
        assertThat(AttendanceService.earliestPunchOut(OFFICE_END,
                java.util.Arrays.asList("", "half three", null, "not a time")))
                .isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("a malformed time alongside a real one leaves the real one standing")
    void oneBadOneGood() {
        assertThat(AttendanceService.earliestPunchOut(OFFICE_END, java.util.Arrays.asList("oops", "13:45")))
                .isEqualTo(LocalTime.of(13, 45));
    }

    @Test
    @DisplayName("the office end time is whatever the company configured")
    void officeEndIsNotHardcoded() {
        assertThat(AttendanceService.earliestPunchOut(LocalTime.of(17, 30), List.of()))
                .isEqualTo(LocalTime.of(17, 30));
    }
}
