package com.pixous.hrportal.modules.notification;

import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sends a copy of what happened to the people who oversee it.
 *
 * <p>Every module already tells the two people in a request -- the person who
 * asked and the person deciding. What was missing was the copy to whoever
 * watches the whole thing, and adding it module by module would have meant six
 * more places that each look up the CTO their own way. The employee code is
 * already hardcoded in five files; this is the sixth reader of it rather than
 * a sixth copy of the rule.
 *
 * <p>Nothing here throws. A notification is a courtesy alongside the thing
 * that actually happened, and a leave request must not fail because the CTO's
 * account could not be read -- so a failure is logged and swallowed, exactly
 * as the existing SMS helper does.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OversightNotifier {

    /** The company head, who receives a copy of every request in the portal. */
    private static final String CTO_CODE = "PIX-E100";

    /** The platform administrator account, which keeps the portal running. */
    private static final String SYSTEM_ADMIN_CODE = "ADM0001";

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Tell the CTO, unless the CTO is the one who did it.
     *
     * <p>Suppressing the self-copy matters: the CTO approves requests too, and
     * being notified of one's own decision is noise that teaches people to
     * ignore the bell.
     */
    /**
     * Whether this account is allowed to read every request in the portal,
     * whoever it was addressed to.
     *
     * <p>Keyed on the account rather than on a permission, because the
     * permissions do not separate these people. HR holds USER_MANAGE -- they
     * manage employee records, which is what the permission is for -- so a
     * check on USER_MANAGE let HR read the complaints somebody had
     * deliberately addressed past them to the CTO. That was the bug this was
     * meant to fix, reintroduced by the exception written for it.
     *
     * <p>The CTO sees everything because the buck stops there. The platform
     * administrator sees everything because a queue they cannot see is one
     * they cannot repair. Nobody else does, HR included.
     */
    public boolean seesEveryRequest(Long userId) {
        if (userId == null) return false;
        try {
            return userRepository.findById(userId)
                    .map(u -> CTO_CODE.equalsIgnoreCase(u.getEmployeeCode())
                            || SYSTEM_ADMIN_CODE.equalsIgnoreCase(u.getEmployeeCode()))
                    .orElse(false);
        } catch (Exception e) {
            log.warn("Could not resolve oversight for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public void notifyCto(Long actorId, String title, String body, String type, String link) {
        try {
            userRepository.findByEmployeeCode(CTO_CODE)
                    .filter(User::isEnabled)
                    .filter(u -> actorId == null || !u.getId().equals(actorId))
                    .ifPresent(u -> notificationService.createAndPush(u.getId(), title, body, type, link));
        } catch (Exception e) {
            log.warn("Could not notify the CTO: {}", e.getMessage());
        }
    }

    /**
     * Tell HR, the administrators and the CTO -- the three who between them
     * need to see work as it is logged.
     *
     * <p>De-duplicated by id, because one person can hold more than one of
     * these permissions and would otherwise be told twice about one event.
     */
    public void notifyOversight(Long actorId, String title, String body, String type, String link) {
        try {
            Set<Long> recipients = new LinkedHashSet<>();
            userRepository.findByPermission("USER_MANAGE").stream()
                    .filter(User::isEnabled).forEach(u -> recipients.add(u.getId()));
            userRepository.findByPermission("COMPLAINT_MANAGE").stream()
                    .filter(User::isEnabled).forEach(u -> recipients.add(u.getId()));
            userRepository.findByEmployeeCode(CTO_CODE)
                    .filter(User::isEnabled).ifPresent(u -> recipients.add(u.getId()));

            if (actorId != null) recipients.remove(actorId);
            recipients.forEach(id -> notificationService.createAndPush(id, title, body, type, link));
        } catch (Exception e) {
            log.warn("Could not notify oversight: {}", e.getMessage());
        }
    }
}
