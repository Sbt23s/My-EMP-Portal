package com.pixous.hrportal.modules.dashboard;

import com.pixous.hrportal.modules.dashboard.dto.Celebration;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Once a day, pushes a notification to every active employee for anyone
 * whose birthday or work anniversary falls today — so the whole team knows
 * to wish them, without anyone having to open the dashboard.
 */
@Component
@RequiredArgsConstructor
public class CelebrationNotifier {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    // 9:00 AM server time (container runs Asia/Kolkata in production).
    @Scheduled(cron = "0 0 9 * * *")
    public void notifyTodaysCelebrations() {
        List<Celebration> todays = dashboardService.todaysCelebrations();
        if (todays.isEmpty()) return;

        List<User> active = userRepository.findByEnabledTrue().stream()
                .filter(u -> !"OFFBOARDED".equalsIgnoreCase(u.getProfileStatus()))
                .toList();

        /*
         * Who each celebration belongs to.
         *
         * This job runs on a timer, so there is no signed-in user and the
         * tenant filter is inactive -- every query here sees every company at
         * once. That is what this method needs in order to cover all of them
         * in one pass, and it is also how one company's birthdays ended up
         * announced inside another's portal: the notification went to every
         * active employee on the platform rather than to the celebrant's own
         * colleagues.
         *
         * The celebration itself does not carry a company, so it is taken from
         * the celebrant's own row, which is the authority on it.
         */
        java.util.Map<Long, Long> companyOfUser = active.stream()
                .filter(u -> u.getCompanyId() != null)
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getCompanyId, (a, b) -> a));

        for (Celebration c : todays) {
            Long celebrantCompany = companyOfUser.get(c.userId());
            if (celebrantCompany == null) {
                // No company on the celebrant means there is no audience that
                // can be established as theirs. Announcing it to everyone is
                // the bug being fixed, so it is skipped instead.
                continue;
            }
            List<User> audience = active.stream()
                    .filter(u -> celebrantCompany.equals(u.getCompanyId()))
                    .toList();
            boolean birthday = "BIRTHDAY".equals(c.type());
            String title = birthday ? "🎂 Birthday today" : "🎉 Work anniversary today";
            String body = birthday
                    ? c.name() + "'s birthday is today" + (c.team() != null ? " (" + c.team() + ")" : "") + " — wish them well!"
                    : c.name() + " completes " + c.years() + " year" + (c.years() != null && c.years() == 1 ? "" : "s")
                            + " with the company today" + (c.team() != null ? " (" + c.team() + ")" : "") + "!";
            for (User u : audience) {
                // Skip notifying the celebrant about their own day.
                if (u.getId().equals(c.userId())) continue;
                notificationService.createAndPush(u.getId(), title, body, "CELEBRATION", "/");
            }
            // One bulk SMS for the celebrant's own company, minus the celebrant.
            smsService.sendBulk(
                    audience.stream()
                            .filter(u -> !u.getId().equals(c.userId()))
                            .map(User::getPhone)
                            .filter(p -> p != null && !p.isBlank())
                            .toList(),
                    "Pixous HR: " + body);
        }
    }
}
