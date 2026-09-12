package com.pixous.hrportal.modules.leave;

import com.pixous.hrportal.modules.leave.dto.LeaveTypeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Editing a leave type must not quietly clear the settings it was not shown.
 *
 * <p>The edit form sends four fields: name, code, max days per year and paid.
 * The service wrote all eleven, so renaming Casual Leave set monthlyLimit to
 * null -- and with it the "one every three months" rule -- along with the
 * notice period, the accrual type, carry-forward and the gender restriction.
 * The save succeeded, and nothing was visibly wrong until somebody applied for
 * leave that should have been refused.
 *
 * <p>These assert the settings survive, because that is the part that was
 * silent. A test that only checked the name would have passed throughout.
 */
class LeaveTypeEditTest {

    private LeaveTypeRepository types;
    private LeaveService service;

    /** Casual Leave as it is configured live. */
    private LeaveType casualLeave() {
        LeaveType t = new LeaveType();
        t.setId(1L);
        t.setCode("CL");
        t.setName("Casual Leave");
        t.setMaxDaysPerYear(4);
        t.setMonthlyLimit(1);          // the quarterly allowance
        t.setMinNoticeDays(2);
        t.setAccrualType("YEARLY");
        t.setCarryForward(true);
        t.setEncashable(true);
        t.setAllowPastDates(true);
        t.setGenderRestriction('F');
        t.setPaid(true);
        return t;
    }

    /** Exactly what the edit dialog sends: four fields, the rest absent. */
    private LeaveTypeRequest formSends(String name, String code, Integer maxDays, Boolean paid) {
        return new LeaveTypeRequest(name, code, maxDays,
                null, null, null, null, null, null, null, paid);
    }

    @BeforeEach
    void setUp() {
        types = Mockito.mock(LeaveTypeRepository.class);
        service = new LeaveService(
                types,
                Mockito.mock(LeaveBalanceRepository.class),
                Mockito.mock(LeaveRequestRepository.class),
                Mockito.mock(com.pixous.hrportal.modules.user.UserRepository.class),
                Mockito.mock(com.pixous.hrportal.modules.org.HolidayRepository.class),
                Mockito.mock(com.pixous.hrportal.modules.attendance.AttendanceRepository.class),
                Mockito.mock(com.pixous.hrportal.modules.notification.NotificationService.class),
                Mockito.mock(com.pixous.hrportal.common.SmsService.class),
                Mockito.mock(com.pixous.hrportal.modules.notification.OversightNotifier.class));
    }

    @Test
    @DisplayName("Renaming a leave type keeps the rule that governs it")
    void renameKeepsTheQuarterlyAllowance() {
        LeaveType stored = casualLeave();
        when(types.findById(1L)).thenReturn(Optional.of(stored));
        when(types.findByCodeIgnoreCase("CL")).thenReturn(Optional.of(stored));
        when(types.save(any(LeaveType.class))).thenAnswer(i -> i.getArgument(0));

        service.updateType(1L, formSends("Casual Leave (CL)", "CL", 4, true));

        // The one that matters: monthlyLimit is what the three-month rule reads.
        assertThat(stored.getMonthlyLimit()).isEqualTo(1);
        assertThat(stored.getName()).isEqualTo("Casual Leave (CL)");
    }

    @Test
    @DisplayName("Every setting the form does not show survives an edit")
    void unsentSettingsAreNotCleared() {
        LeaveType stored = casualLeave();
        when(types.findById(1L)).thenReturn(Optional.of(stored));
        when(types.findByCodeIgnoreCase("CL")).thenReturn(Optional.of(stored));
        when(types.save(any(LeaveType.class))).thenAnswer(i -> i.getArgument(0));

        service.updateType(1L, formSends("Casual Leave", "CL", 4, true));

        assertThat(stored.getMinNoticeDays()).isEqualTo(2);
        assertThat(stored.getAccrualType()).isEqualTo("YEARLY");
        assertThat(stored.isCarryForward()).isTrue();
        assertThat(stored.isEncashable()).isTrue();
        assertThat(stored.isAllowPastDates()).isTrue();
        assertThat(stored.getGenderRestriction()).isEqualTo('F');
    }

    @Test
    @DisplayName("What the form does send is applied")
    void sentFieldsAreApplied() {
        LeaveType stored = casualLeave();
        when(types.findById(1L)).thenReturn(Optional.of(stored));
        when(types.findByCodeIgnoreCase("CL")).thenReturn(Optional.of(stored));
        when(types.save(any(LeaveType.class))).thenAnswer(i -> i.getArgument(0));

        service.updateType(1L, formSends("Casual", "CL", 6, false));

        assertThat(stored.getName()).isEqualTo("Casual");
        assertThat(stored.getMaxDaysPerYear()).isEqualTo(6);
        assertThat(stored.isPaid()).isFalse();
    }

    @Test
    @DisplayName("A blank gender restriction clears it; an absent one does not")
    void blankIsDeliberateAndAbsentIsNot() {
        LeaveType stored = casualLeave();
        when(types.findById(1L)).thenReturn(Optional.of(stored));
        when(types.findByCodeIgnoreCase("CL")).thenReturn(Optional.of(stored));
        when(types.save(any(LeaveType.class))).thenAnswer(i -> i.getArgument(0));

        // Sending "" is somebody choosing "no restriction" in a form that has
        // the field. Not sending it at all is a form that does not.
        service.updateType(1L, new LeaveTypeRequest(
                "Casual Leave", "CL", 4, null, null, "", null, null, null, null, true));
        assertThat(stored.getGenderRestriction()).isNull();
    }
}
