package com.pixous.hrportal.modules.dashboard;

import com.pixous.hrportal.common.SmsService;
import com.pixous.hrportal.modules.dashboard.dto.Celebration;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A celebration belongs to one company, and so does its audience.
 *
 * <p>The notifier runs on a timer, with no signed-in user, so the tenant
 * filter is inactive and every query sees the whole platform. It needs that to
 * cover all the companies in one pass -- and it is also how one company's
 * birthdays were announced inside another company's portal.
 */
@ExtendWith(MockitoExtension.class)
class CelebrationTenantScopeTest {

    @Mock DashboardService dashboardService;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock SmsService smsService;

    @InjectMocks CelebrationNotifier notifier;

    private static User user(long id, Long companyId) {
        User u = new User();
        u.setId(id);
        u.setCompanyId(companyId);
        u.setName("User " + id);
        u.setEnabled(true);
        u.setProfileStatus("ACTIVE");
        return u;
    }

    private static Celebration birthdayOf(long userId) {
        return new Celebration(userId, "Amutha Kumari G", "PIX-E120", "Mobile Developer",
                null, "BIRTHDAY", LocalDate.now(), 0, null);
    }

    @Test
    void aBirthdayReachesOnlyTheCelebrantsOwnCompany() {
        long pixous = 1L, sethu = 2L;
        User celebrant = user(10L, pixous);
        User pixousColleague = user(11L, pixous);
        User sethuAdmin = user(20L, sethu);

        when(dashboardService.todaysCelebrations()).thenReturn(List.of(birthdayOf(10L)));
        when(userRepository.findByEnabledTrue())
                .thenReturn(List.of(celebrant, pixousColleague, sethuAdmin));

        notifier.notifyTodaysCelebrations();

        // The colleague in the same company hears about it.
        verify(notificationService).createAndPush(
                eq(11L), anyString(), anyString(), eq("CELEBRATION"), anyString());
        // The other company's admin does not -- this is the reported bug.
        verify(notificationService, never()).createAndPush(
                eq(20L), anyString(), anyString(), anyString(), anyString());
        // Nor does the celebrant, about their own day.
        verify(notificationService, never()).createAndPush(
                eq(10L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aCelebrantWithNoCompanyAnnouncesToNobody() {
        // Rather than to everybody, which is what "no company" used to mean.
        User orphan = user(30L, null);
        User someoneElse = user(31L, 1L);

        when(dashboardService.todaysCelebrations()).thenReturn(List.of(birthdayOf(30L)));
        when(userRepository.findByEnabledTrue()).thenReturn(List.of(orphan, someoneElse));

        notifier.notifyTodaysCelebrations();

        verify(notificationService, never()).createAndPush(
                any(), anyString(), anyString(), anyString(), anyString());
    }
}
