package com.pixous.hrportal.modules.attendance;

import com.pixous.hrportal.modules.leave.LeaveRequest;
import com.pixous.hrportal.modules.leave.LeaveRequestRepository;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.org.HolidayRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The morning digest: who is on leave, who simply did not turn up, and who
 * gets told.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyAbsenceNotifierTest {

    @Mock UserRepository userRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock LeaveRequestRepository leaveRequestRepository;
    @Mock HolidayRepository holidayRepository;
    @Mock NotificationService notificationService;

    @InjectMocks DailyAbsenceNotifier notifier;

    private static final LocalDate DAY = LocalDate.of(2026, 9, 2); // a Wednesday

    private static User user(long id, Long companyId, String code) {
        User u = new User();
        u.setId(id);
        u.setCompanyId(companyId);
        u.setName("User " + id);
        u.setEmployeeCode(code);
        u.setEnabled(true);
        u.setProfileStatus("ACTIVE");
        return u;
    }

    private static Attendance punch(long userId) {
        Attendance a = new Attendance();
        a.setUserId(userId);
        a.setWorkDate(DAY);
        a.setPunchInAt(LocalDateTime.of(2026, 9, 2, 9, 30));
        return a;
    }

    private static LeaveRequest leave(long userId) {
        LeaveRequest r = new LeaveRequest();
        r.setUserId(userId);
        return r;
    }

    @Test
    void separatesApprovedLeaveFromUnexplainedAbsence() {
        User hr = user(1L, 100L, "PIX-H001");
        User present = user(2L, 100L, "PIX-E002");
        User onLeave = user(3L, 100L, "PIX-E003");
        User absent = user(4L, 100L, "PIX-E004");

        when(userRepository.findAll()).thenReturn(List.of(hr, present, onLeave, absent));
        when(attendanceRepository.findByWorkDate(DAY))
                .thenReturn(List.of(punch(1L), punch(2L)));
        when(leaveRequestRepository.findOnLeave(DAY)).thenReturn(List.of(leave(3L)));
        when(userRepository.findByPermission("USER_MANAGE")).thenReturn(List.of(hr));
        when(userRepository.findByPermission("COMPLAINT_MANAGE")).thenReturn(List.of());
        when(userRepository.findByEmployeeCode("PIX-E100")).thenReturn(Optional.empty());

        notifier.sendFor(DAY);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndPush(
                eq(1L), title.capture(), body.capture(), eq("ATTENDANCE_DIGEST"), anyString());

        // User 4 never punched in and has no leave; user 3 is accounted for.
        assertThat(title.getValue()).isEqualTo("Today: 1 absent, 1 on leave");
        assertThat(body.getValue()).contains("Absent (no leave applied): User 4 (PIX-E004)");
        assertThat(body.getValue()).contains("On approved leave: User 3 (PIX-E003)");
        // Somebody who punched in is in neither list.
        assertThat(body.getValue()).doesNotContain("User 2");
    }

    @Test
    void oneCompanyIsNeverToldAboutAnothersStaff() {
        User pixousHr = user(1L, 100L, "PIX-H001");
        User pixousAbsent = user(2L, 100L, "PIX-E002");
        User sethuHr = user(10L, 200L, "SET-H001");
        User sethuAbsent = user(11L, 200L, "SET-E011");

        when(userRepository.findAll())
                .thenReturn(List.of(pixousHr, pixousAbsent, sethuHr, sethuAbsent));
        when(attendanceRepository.findByWorkDate(DAY)).thenReturn(List.of(punch(1L), punch(10L)));
        when(leaveRequestRepository.findOnLeave(DAY)).thenReturn(List.of());
        when(userRepository.findByPermission("USER_MANAGE")).thenReturn(List.of(pixousHr, sethuHr));
        when(userRepository.findByPermission("COMPLAINT_MANAGE")).thenReturn(List.of());
        when(userRepository.findByEmployeeCode("PIX-E100")).thenReturn(Optional.empty());

        notifier.sendFor(DAY);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndPush(
                eq(1L), anyString(), body.capture(), anyString(), anyString());
        assertThat(body.getValue()).contains("User 2").doesNotContain("User 11");

        ArgumentCaptor<String> sethuBody = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createAndPush(
                eq(10L), anyString(), sethuBody.capture(), anyString(), anyString());
        assertThat(sethuBody.getValue()).contains("User 11").doesNotContain("User 2");
    }

    @Test
    void fullAttendanceSendsNothing() {
        // A bell that rings on a day with nothing to report is one people
        // stop opening.
        User hr = user(1L, 100L, "PIX-H001");
        User present = user(2L, 100L, "PIX-E002");

        when(userRepository.findAll()).thenReturn(List.of(hr, present));
        when(attendanceRepository.findByWorkDate(DAY)).thenReturn(List.of(punch(1L), punch(2L)));
        when(leaveRequestRepository.findOnLeave(DAY)).thenReturn(List.of());

        notifier.sendFor(DAY);

        verify(notificationService, never()).createAndPush(
                any(), anyString(), anyString(), anyString(), anyString());
    }
}
