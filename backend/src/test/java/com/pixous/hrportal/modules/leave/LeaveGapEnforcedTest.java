package com.pixous.hrportal.modules.leave;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.org.HolidayRepository;
import com.pixous.hrportal.modules.leave.dto.LeaveApplyRequest;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * "One Casual or Sick leave every three months" -- asked of the service itself.
 *
 * <p>QuarterlyLeaveGapTest beside this one checks the arithmetic, but it does
 * it on a copy of the rule written into the test. That tells us the sum is
 * right; it cannot tell us the service still performs it. The comment at the
 * top of apply() even says the three-month rule "has been removed", which it
 * is not -- so the one thing worth asserting is that a second request actually
 * comes back refused.
 *
 * <p>The repositories are mocked because the rule is a decision, not a query:
 * what matters is what apply() does when the repository reports an earlier
 * leave, and a real database would only make that harder to state.
 */
class LeaveGapEnforcedTest {

    private LeaveTypeRepository types;
    private LeaveRequestRepository requests;
    private LeaveBalanceRepository balances;
    private UserRepository users;
    private LeaveService service;

    /** A leave type carrying the quarterly allowance, as CL and SL do live. */
    private LeaveType typeOf(String code, String name) {
        LeaveType t = new LeaveType();
        t.setId(1L);
        t.setCode(code);
        t.setName(name);
        t.setMonthlyLimit(1);          // one per quarter
        t.setAllowPastDates(true);     // keep today's date out of the assertion
        return t;
    }

    @BeforeEach
    void setUp() {
        types = Mockito.mock(LeaveTypeRepository.class);
        requests = Mockito.mock(LeaveRequestRepository.class);
        balances = Mockito.mock(LeaveBalanceRepository.class);
        users = Mockito.mock(UserRepository.class);

        service = new LeaveService(
                types, balances, requests, users,
                Mockito.mock(HolidayRepository.class),
                Mockito.mock(com.pixous.hrportal.modules.attendance.AttendanceRepository.class),
                Mockito.mock(com.pixous.hrportal.modules.notification.NotificationService.class),
                Mockito.mock(com.pixous.hrportal.common.SmsService.class),
                Mockito.mock(com.pixous.hrportal.modules.notification.OversightNotifier.class));

        User u = new User();
        u.setId(7L);
        u.setName("Test Employee");
        when(users.findById(7L)).thenReturn(Optional.of(u));

        /*
          A balance with days left on it.

          Without this the balance check -- which runs after the two rules
          these tests are about -- refused every one of them with "No leave
          balance allocated", so they passed while never reaching the rule
          they name. Green, and proving nothing. The last test overrides this
          with an empty balance deliberately, because it is asserting that
          whatever stops the first request is not one of these two rules.
        */
        LeaveBalance bal = new LeaveBalance();
        bal.setAllocated(new java.math.BigDecimal("12"));
        bal.setUsed(java.math.BigDecimal.ZERO);
        when(balances.findByUserIdAndLeaveTypeIdAndYear(anyLong(), anyLong(), Mockito.anyInt()))
                .thenReturn(Optional.of(bal));
        when(requests.findOverlapping(anyLong(), any(), any(), any())).thenReturn(List.of());
    }

    /** A weekday range, so the working-day guard is not what refuses it. */
    private LeaveApplyRequest request(LocalDate from, LocalDate to) {
        return new LeaveApplyRequest(1L, from, to, "reason", null, null);
    }

    @Test
    @DisplayName("A second casual leave in the same quarter is refused")
    void secondCasualLeaveInQuarterIsRefused() {
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("CL", "Casual Leave")));
        // One already on the books this quarter -- pending counts, which is the
        // case that matters: the first has not been approved yet.
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.apply(7L, request(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 28))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("per 3 months");
    }

    @Test
    @DisplayName("Sick leave is refused on the same rule, not only casual")
    void sickLeaveIsRefusedToo() {
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("SL", "Sick Leave")));
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.apply(7L, request(
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 28))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("per 3 months");
    }

    @Test
    @DisplayName("A second one in a later quarter is still refused inside three months")
    void nextQuarterButInsideThreeMonthsIsRefused() {
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("CL", "Casual Leave")));
        // Nothing in this calendar quarter, so only the rolling gap can catch it:
        // last taken 30 September, asked for 1 October -- one day apart.
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(0L);
        when(requests.findLatestDayTaken(anyLong(), anyLong()))
                .thenReturn(LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> service.apply(7L, request(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("once every three months");
    }

    @Test
    @DisplayName("A two-day casual leave is refused: these are one day at a time")
    void casualLeaveIsOneDayOnly() {
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("CL", "Casual Leave")));
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(0L);
        when(requests.findLatestDayTaken(anyLong(), anyLong())).thenReturn(null);

        // The quarterly cap counts requests, so a single request spanning a
        // week satisfied it. The length is a separate question.
        assertThatThrownBy(() -> service.apply(7L, request(
                LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 18))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("one day at a time");
    }

    @Test
    @DisplayName("Sick leave is one day at a time as well")
    void sickLeaveIsOneDayOnly() {
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("SL", "Sick Leave")));
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(0L);
        when(requests.findLatestDayTaken(anyLong(), anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.apply(7L, request(
                LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 17))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("one day at a time");
    }

    @Test
    @DisplayName("A single-day casual leave passes the length check")
    void oneDayIsAllowed() {
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("CL", "Casual Leave")));
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(0L);
        when(requests.findLatestDayTaken(anyLong(), anyLong())).thenReturn(null);

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.apply(7L, request(
                        LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 16))));
        if (t != null) {
            assertThat(t.getMessage()).doesNotContain("one day at a time");
        }
    }

    @Test
    @DisplayName("Leave with no such limit may still span days")
    void otherTypesMaySpanDays() {
        // Earned leave is taken a week at a time; the one-day rule names CL
        // and SL only and must not reach anything else.
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("EL", "Earned Leave")));
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(0L);
        when(requests.findLatestDayTaken(anyLong(), anyLong())).thenReturn(null);

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.apply(7L, request(
                        LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 18))));
        if (t != null) {
            assertThat(t.getMessage()).doesNotContain("one day at a time");
        }
    }

    @Test
    @DisplayName("The first request of all is not refused by either rule")
    void theFirstRequestPasses() {
        when(types.findById(1L)).thenReturn(Optional.of(typeOf("CL", "Casual Leave")));
        when(requests.countRequestsInRange(anyLong(), anyLong(), any(), any())).thenReturn(0L);
        when(requests.findLatestDayTaken(anyLong(), anyLong())).thenReturn(null);
        when(balances.findByUserIdAndLeaveTypeIdAndYear(anyLong(), anyLong(), Mockito.anyInt()))
                .thenReturn(Optional.empty());

        // It gets past both gates. Whatever stops it after this is a different
        // rule (a balance, an approver) and not the concern of this test.
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.apply(7L, request(
                        LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 28))));
        if (t != null) {
            assertThat(t.getMessage()).doesNotContain("per 3 months");
            assertThat(t.getMessage()).doesNotContain("once every three months");
        }
    }
}
