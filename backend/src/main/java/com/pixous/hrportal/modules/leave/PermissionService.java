package com.pixous.hrportal.modules.leave;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.leave.dto.PermissionApplyRequest;
import com.pixous.hrportal.modules.leave.dto.PermissionResponse;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PermissionService {

    /** The working day permission has to sit inside. */
    private static final LocalTime WORK_DAY_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_DAY_END = LocalTime.of(18, 0);

    /** The most time off one day's permission may carry. */
    private static final long MAX_PERMISSION_MINUTES = 120;

    private final PermissionRequestRepository repo;
    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final com.pixous.hrportal.modules.notification.NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;
    private final com.pixous.hrportal.modules.notification.OversightNotifier oversight;

    @Transactional
    public PermissionResponse apply(Long userId, PermissionApplyRequest req) {
        LocalTime from, to;
        try {
            from = LocalTime.parse(req.fromTime());
            to = LocalTime.parse(req.toTime());
        } catch (Exception e) {
            throw ApiException.business("Invalid time — use HH:mm");
        }
        if (!to.isAfter(from)) {
            throw ApiException.business("End time must be after start time");
        }
        /*
         * A permission on a Saturday or Sunday is a mistake, not a request.
         *
         * Permission is time off within a working day, so there is no working
         * day for it to be within. Refused here rather than approved and then
         * counted against hours nobody was due to work.
         */
        if (com.pixous.hrportal.common.WorkCalendar.isWeekend(req.requestDate())) {
            throw ApiException.business(
                    "Permission cannot be taken on a "
                            + req.requestDate().getDayOfWeek().getDisplayName(
                                    java.time.format.TextStyle.FULL,
                                    java.util.Locale.ENGLISH)
                            + ". Saturdays and Sundays are not working days.");
        }

        /*
         * One permission per person per day.
         *
         * A second request for a day that already has one leaves an approver
         * choosing between two versions of the same absence, and if both are
         * approved the hours are counted twice. The repository already had a
         * query shaped for this question and nothing called it.
         */
        List<PermissionRequest> sameDay = repo.findLiveOnDate(userId, req.requestDate());
        if (!sameDay.isEmpty()) {
            PermissionRequest existing = sameDay.get(0);
            throw ApiException.business(
                    "You already have a permission on " + req.requestDate()
                            + " from " + existing.getFromTime() + " to "
                            + existing.getToTime() + " ("
                            + existing.getStatus().toLowerCase() + "). "
                            + "Only one permission per day is allowed. "
                            + "Cancel that request first, or choose another date.");
        }

        /*
         * Permission is time off inside the working day, so it has to fall
         * inside one. Without this the form would take 2am to 4am and the
         * hours were counted as time off from a day that had not started.
         */
        if (from.isBefore(WORK_DAY_START) || to.isAfter(WORK_DAY_END)) {
            throw ApiException.business(
                    "Permission can only be taken between 9:00 AM and 6:00 PM.");
        }

        /*
         * Two hours is the most in one day. Beyond that it stops being short
         * time off and becomes leave, which is a different request with a
         * different approval path and a balance to come out of.
         */
        long minutes = Duration.between(from, to).toMinutes();
        if (minutes > MAX_PERMISSION_MINUTES) {
            throw ApiException.business(
                    "Permission is limited to 2 hours a day. That range is "
                            + describeMinutes(minutes)
                            + " — apply for leave instead.");
        }

        /*
         * Leave already booked on the day means the person is not at work to
         * take time off from. Both records would otherwise stand, and the day
         * would be counted once as leave and again as permission hours.
         *
         * Read from the leave side deliberately: it is the record that already
         * knows about ranges, and its query already ignores rejected and
         * cancelled requests, which never consumed the day.
         */
        List<com.pixous.hrportal.modules.leave.LeaveRequest> onLeave =
                leaveRequestRepository.findOverlapping(
                        userId, req.requestDate(), req.requestDate(), null);
        if (!onLeave.isEmpty()) {
            com.pixous.hrportal.modules.leave.LeaveRequest l = onLeave.get(0);
            throw ApiException.business(
                    "You already have leave on " + req.requestDate()
                            + " (" + l.getStatus().toLowerCase() + "). "
                            + "Permission cannot be taken on a day already booked as leave.");
        }

        BigDecimal hours = BigDecimal.valueOf(Duration.between(from, to).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        PermissionRequest p = new PermissionRequest();
        p.setUserId(userId);
        p.setRequestDate(req.requestDate());
        p.setFromTime(req.fromTime());
        p.setToTime(req.toTime());
        p.setHours(hours);
        p.setReason(req.reason());
        p.setPriority(normalisePriority(req.priority()));
        p.setRequestedTo(req.requestedTo());
        p.setStatus("PENDING");
        PermissionRequest saved = repo.save(p);

        if (req.requestedTo() != null) {
            String applicantName = userRepository.findById(userId).map(User::getName).orElse("Someone");
            String detail = applicantName + " requested " + hours + "h permission on " + req.requestDate()
                    + " (" + req.fromTime() + "–" + req.toTime() + ")";

            /*
             * Everybody who can act on it, not only the account it was
             * addressed to.
             *
             * HR is a desk. A request went to whichever HR name the approver
             * list happened to offer, and only that account was told about it
             * -- so if they were on leave the request sat unanswered and nobody
             * else knew it existed. The whole desk now hears about it, and the
             * first one free can act.
             *
             * The addressed approver is always included: for an employee's
             * request that is their team leader, who is not HR and must still
             * be told.
             */
            java.util.Map<Long, User> recipients = new java.util.LinkedHashMap<>();
            userRepository.findById(req.requestedTo()).ifPresent(u -> recipients.put(u.getId(), u));
            if (isHrRequest(req.requestedTo())) {
                for (User hr : userRepository.findByRoleCodes(HR_ROLE_CODES)) {
                    recipients.put(hr.getId(), hr);
                }
            }
            // Never the applicant's own inbox, even when they are in HR.
            recipients.remove(userId);

            for (User r : recipients.values()) {
                notificationService.createAndPush(r.getId(),
                        "New permission request", detail, "PERMISSION", "/leave/permissions");
            }

            // A copy to the CTO, who follows every request in the portal
            // without being in the approval chain for most of them.
            oversight.notifyCto(userId, "New permission request", detail,
                    "PERMISSION", "/leave/permissions");

            /*
             * One text message, to the person it was addressed to.
             *
             * Deliberately not to the whole desk: an in-app notification costs
             * nothing and a text costs money and interrupts an evening. The
             * addressed approver is the one being asked; the rest can see it
             * when they next look.
             */
            userRepository.findById(req.requestedTo())
                    .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                    .ifPresent(u -> smsService.send(u.getPhone(),
                            "Pixous HR: " + detail + ". Please review in the portal."));
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> mine(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    /**
     * Whether this person sees HR's whole queue rather than only their own.
     *
     * <p>HR is a desk, not a person. A request addressed to whoever happened to
     * be offered in the approver list was visible to that one account and
     * nobody else, so a permission sat unanswered while the rest of HR had no
     * idea it existed — and an approval one of them made was invisible to the
     * others.
     *
     * <p>Leave already worked this way: {@code pendingForManager} shows the
     * whole queue to anyone holding IT_MGR or IT_HR. Permission did not, which
     * is the difference this closes.
     */
    private boolean seesTheWholeQueue(User u) {
        return u != null
                && (hasRole(u, "IT_HR") || hasRole(u, "CV_HR") || hasRole(u, "IT_MGR")
                    || hasRole(u, "SUPER_ADMIN")
                    || com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE"));
    }

    /**
     * Requests waiting on this approver.
     *
     * <p>For a team leader that is the ones addressed to them. For anyone in HR
     * it is every pending request, because any of them can act on it and a
     * queue only one person can see is a queue that stalls when that person is
     * away.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> pendingFor(Long approverId) {
        User me = userRepository.findById(approverId).orElse(null);
        if (seesTheWholeQueue(me)) {
            return repo.findAllByOrderByCreatedAtDesc().stream()
                    .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                    .map(this::toResponse).toList();
        }
        return repo.findByStatusAndRequestedTo("PENDING", approverId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Every request this approver may look at, whatever its status.
     *
     * <p>Same rule as above, and the reason it matters after a decision as
     * much as before: an approval made by one HR account was previously
     * invisible to the rest, so nobody else could see what had been agreed.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> forApprover(Long approverId) {
        User me = userRepository.findById(approverId).orElse(null);
        if (seesTheWholeQueue(me)) {
            return repo.findAllByOrderByCreatedAtDesc().stream()
                    .map(this::toResponse).toList();
        }
        List<PermissionRequest> list = repo.findByRequestedToOrderByCreatedAtDesc(approverId);
        if (list == null || list.isEmpty()) {
            list = repo.findAllByOrderByCreatedAtDesc().stream()
                    .filter(r -> r.getRequestedTo() == null || approverId.equals(r.getRequestedTo()))
                    .toList();
        }
        return list.stream().map(this::toResponse).toList();
    }

    /** Employee code of the company head, who approves HR's own requests. */
    private static final String HR_APPROVER_CODE = "PIX-E100";

    /**
     * The roles that make somebody part of the HR desk.
     *
     * <p>IT_MGR is deliberately absent. It counts as HR for deciding who may
     * approve, and including it here would copy every permission request to
     * two managers who are not on the desk.
     */
    private static final java.util.List<String> HR_ROLE_CODES =
            java.util.List.of("IT_HR", "CV_HR");

    /** Whether a request addressed to this person is a request to HR. */
    private boolean isHrRequest(Long approverId) {
        if (approverId == null) {
            return false;
        }
        return userRepository.findById(approverId)
                .map(u -> hasRole(u, "IT_HR") || hasRole(u, "CV_HR"))
                .orElse(false);
    }

    /**
     * Approvers the requester may send a permission to. Exactly one level, so
     * the list never offers a choice that skips someone:
     *  - Employee    -> their own team's Team Leader only
     *  - Team Leader -> HR only
     *  - HR          -> the company head (PIX-E100) or the System Admin
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> approvers(Long requesterId) {
        User me = requesterId == null ? null : userRepository.findById(requesterId).orElse(null);
        if (me == null) return List.of();

        boolean iAmHr = hasRole(me, "IT_MGR") || hasRole(me, "IT_HR") || hasRole(me, "CV_HR");
        boolean iAmTl = hasRole(me, "IT_TL") || hasRole(me, "CV_SUP");

        java.util.function.Predicate<User> allowed;
        if (iAmHr) {
            /*
              HR asks the CTO, and only the CTO.

              This also admitted anybody holding SUPER_ADMIN or COMPANY_ADMIN,
              which in this company is five accounts: the CTO plus a system
              administrator and three others carrying the company-admin role.
              So HR opening the form was asked to choose an approver from a
              list of five, four of whom have no business approving their
              hours -- they hold the role to configure the portal, not to
              manage HR's time.

              There is exactly one person above HR, and naming them is the
              whole point of the rung.
            */
            allowed = u -> "PIX-E100".equalsIgnoreCase(u.getEmployeeCode());
        } else if (iAmTl) {
            /*
              A Team Leader's permission goes to HR, and to HR alone.

              This offered the CTO and every administrator alongside HR, so a
              Team Leader chose their own approver from five names. A rung that
              offers a choice is not a rung: the request skips the person whose
              job it is and lands with whoever was first in the list. The CTO's
              place in this chain is above HR, not beside them.
            */
            allowed = u -> hasRole(u, "IT_MGR") || hasRole(u, "IT_HR") || hasRole(u, "CV_HR");
        } else {
            // Employee permission request -> only their own team's TL.
            //
            // This matched on departmentId, which is not how a team is defined
            // here and is unset for most records, so the dropdown came up empty
            // and the form could not be submitted at all. Teams are
            // designations - see TeamMates.
            allowed = u -> (hasRole(u, "IT_TL") || hasRole(u, "CV_SUP"))
                    && TeamMates.sameTeam(me, u);
        }

        List<User> pool = userRepository.findByEnabledTrue().stream()
                .filter(u -> !u.getId().equals(requesterId))
                .filter(allowed)
                .toList();

        // A team leader's request goes to HR, so narrow to the people whose job
        // that actually is. The branch above also admits IT_MGR, the manager
        // role, along with administrators -- which listed two managers beside HR
        // and offered a choice where the chain is meant to have one rung. Kept a
        // preference, not a hard filter: with nobody holding IT_HR or CV_HR an
        // empty dropdown would make the form unsubmittable.
        if (iAmTl) {
            List<User> realHr = pool.stream()
                    .filter(u -> hasRole(u, "IT_HR") || hasRole(u, "CV_HR"))
                    .toList();
            if (!realHr.isEmpty()) pool = realHr;
        }

        // Falling back to any team leader beats an empty dropdown, for the same
        // reason as in LeaveService: a request that reaches the wrong approver
        // can be redirected, a form that cannot be submitted cannot.
        if (pool.isEmpty() && !iAmHr && !iAmTl) {
            pool = userRepository.findByEnabledTrue().stream()
                    .filter(u -> !u.getId().equals(requesterId))
                    .filter(u -> hasRole(u, "IT_TL") || hasRole(u, "CV_SUP"))
                    .toList();
        }

        return pool.stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    String name = u.getName();
                    if ("PIX-E100".equalsIgnoreCase(u.getEmployeeCode())
                            && (name == null || name.isBlank()
                                || "CEO".equalsIgnoreCase(name) || "CTO".equalsIgnoreCase(name))) {
                        // Stored as "CEO"; the company calls the post CTO and
                        // the person by name, so say the name and let the role
                        // label above supply the title.
                        name = "Elamaran Subramaniyan";
                    }
                    m.put("name", name);
                    // What they are to the requester, so this dropdown reads
                    // "TL - Harish C" like the leave one rather than a bare name.
                    String label;
                    if ("PIX-E100".equalsIgnoreCase(u.getEmployeeCode())) label = "CTO";
                    else if (hasRole(u, "IT_MGR") || hasRole(u, "IT_HR") || hasRole(u, "CV_HR")) label = "HR";
                    else if (hasRole(u, "IT_TL") || hasRole(u, "CV_SUP")) label = "TL";
                    else label = "Approver";
                    m.put("role", label);
                    m.put("code", u.getEmployeeCode());
                    return m;
                }).toList();
    }

    /**
     * Whether this person holds the role, treating COMPANY_ADMIN as SUPER_ADMIN.
     *
     * <p>Same reason as in LeaveService: a company's top administrator is
     * COMPANY_ADMIN, and asked by its literal name this said no — so the person
     * meant to decide short-permission requests could not.
     */
    private static boolean hasRole(User u, String code) {
        return u.getRoles().stream().anyMatch(r -> {
            String held = r.getCode();
            if (code.equals(held)) return true;
            return "SUPER_ADMIN".equals(code) && "COMPANY_ADMIN".equals(held);
        });
    }

    @Transactional
    public PermissionResponse decide(Long deciderId, Long id, boolean approve, String comment) {
        PermissionRequest p = repo.findById(id).orElseThrow(() -> ApiException.notFound("Permission request"));
        if (p.getUserId().equals(deciderId)) {
            throw ApiException.business("You cannot approve or reject your own permission request");
        }
        /*
          Only the person the request names may decide it.

          There was an administrator override here: anyone holding SUPER_ADMIN
          or COMPANY_ADMIN could approve or reject any request, whoever it was
          addressed to. That quietly made the chain optional -- an employee's
          hours could be approved by somebody who has never met them, and HR
          could be bypassed on a Team Leader's request.

          The chain exists so a request reaches somebody who can judge it.
          Administrators and the CTO still see everything; seeing is not
          deciding, and the two were conflated.
        */
        boolean isDirectApprover = p.getRequestedTo() != null && p.getRequestedTo().equals(deciderId);

        /*
          One exception, and only one: a request addressed to HR may be decided
          by anyone on the HR desk.

          HR is a desk rather than a person. The approver list offers whichever
          HR account it happens to offer, and holding the request to that one
          account meant a permission waited for somebody who was on leave while
          three colleagues who could have answered it were told they were not
          allowed to.

          This does not reopen the administrator override the note above
          removed. An administrator is not on the HR desk, a Team Leader's
          request still reaches HR rather than another TL, and an employee's
          request still reaches their own Team Leader -- the rung is unchanged,
          only its width.
         */
        boolean isHrDeskRequest = isHrRequest(p.getRequestedTo());
        User decider = userRepository.findById(deciderId).orElse(null);
        boolean deciderIsHrDesk = decider != null
                && (hasRole(decider, "IT_HR") || hasRole(decider, "CV_HR"));

        if (!isDirectApprover && !(isHrDeskRequest && deciderIsHrDesk)) {
            throw ApiException.business(
                    "Only the approver this request was sent to can approve or reject it.");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw ApiException.business("A reason is required to reject a permission request");
        }
        // The day has already passed, so approving or rejecting it now decides
        // nothing — the request stands as overdue.
        if ("PENDING".equals(p.getStatus()) && p.getRequestDate() != null
                && p.getRequestDate().isBefore(java.time.LocalDate.now())) {
            throw ApiException.business(
                    "This request was for " + p.getRequestDate()
                            + " and is now overdue — it can no longer be approved or rejected");
        }
        p.setStatus(approve ? "APPROVED" : "REJECTED");
        p.setDecidedBy(deciderId);
        p.setDecidedAt(LocalDateTime.now());
        p.setDecisionComment(comment);
        PermissionRequest saved = repo.save(p);

        String verb = approve ? "approved" : "rejected";
        String detail = "Your permission request for " + p.getRequestDate() + " was " + verb
                + (comment != null && !comment.isBlank() ? ": " + comment : ".");
        notificationService.createAndPush(p.getUserId(),
                "Permission " + verb, detail, "PERMISSION", "/leave/permissions");

        // The decision, not just the request: who decided and what they
        // decided is the half the CTO could not see before.
        String applicant = userRepository.findById(p.getUserId())
                .map(User::getName).orElse("Someone");
        oversight.notifyCto(deciderId, "Permission " + verb,
                applicant + "'s permission for " + p.getRequestDate() + " was " + verb
                        + " by " + userRepository.findById(deciderId)
                                .map(User::getName).orElse("their approver") + ".",
                "PERMISSION", "/leave/permissions");
        /*
         * The rest of the desk, when the desk decided it.
         *
         * Any one of them could have answered this request and all of them were
         * shown it as pending. Telling only the applicant leaves the others
         * looking at a queue that is now wrong -- and, because the browser
         * refreshes these lists off the notification, looking at it until they
         * reload the page.
         *
         * Told about the decision rather than asked to act: no text message,
         * and nothing sent to whoever made it.
         */
        if (isHrRequest(p.getRequestedTo())) {
            String deciderName = userRepository.findById(deciderId)
                    .map(User::getName).orElse("A colleague");
            String deskDetail = applicant + "'s permission for " + p.getRequestDate()
                    + " was " + verb + " by " + deciderName + ".";
            for (User hr : userRepository.findByRoleCodes(HR_ROLE_CODES)) {
                if (hr.getId().equals(deciderId) || hr.getId().equals(p.getUserId())) {
                    continue;
                }
                notificationService.createAndPush(hr.getId(),
                        "Permission " + verb, deskDetail, "PERMISSION", "/leave/permissions");
            }
        }

        userRepository.findById(p.getUserId())
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + detail));
        return toResponse(saved);
    }

    /** One of HIGH | MEDIUM | LOW; anything else reads as MEDIUM. */
    /**
     * "2h 30m" rather than "150", so the message says the thing the person
     * chose in the units they chose it in.
     */
    private static String describeMinutes(long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    /**
     * Whether a date can carry a permission request, and the reason when it
     * cannot.
     *
     * <p>The same three questions apply() asks, asked without writing
     * anything: is it a working day, is there already a permission on it, and
     * is the person on leave. apply() still runs all of them -- this is so the
     * form can say so while the date is still on screen, and a stale answer
     * here can only be wrong in the direction of letting a submit through to
     * the check that actually decides.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> availability(Long userId, java.time.LocalDate date) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("date", date.toString());

        if (com.pixous.hrportal.common.WorkCalendar.isWeekend(date)) {
            out.put("available", false);
            out.put("reason", date.getDayOfWeek().getDisplayName(
                            java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                    + "s are not working days — permission can only be taken on a working day.");
            return out;
        }

        List<com.pixous.hrportal.modules.leave.LeaveRequest> onLeave =
                leaveRequestRepository.findOverlapping(userId, date, date, null);
        if (!onLeave.isEmpty()) {
            com.pixous.hrportal.modules.leave.LeaveRequest l = onLeave.get(0);
            out.put("available", false);
            out.put("reason", "You have already applied for leave on this date ("
                    + l.getStatus().toLowerCase()
                    + "). Permission cannot be taken on a day already booked as leave.");
            return out;
        }

        List<PermissionRequest> sameDay = repo.findLiveOnDate(userId, date);
        if (!sameDay.isEmpty()) {
            PermissionRequest existing = sameDay.get(0);
            out.put("available", false);
            out.put("reason", "You already have a permission on this date, "
                    + existing.getFromTime() + " to " + existing.getToTime()
                    + " (" + existing.getStatus().toLowerCase()
                    + "). Only one permission per day is allowed.");
            return out;
        }

        out.put("available", true);
        return out;
    }

    private static String normalisePriority(String raw) {
        String p = raw == null ? "" : raw.trim().toUpperCase();
        return switch (p) {
            case "HIGH", "LOW" -> p;
            default -> "MEDIUM";
        };
    }

    /**
     * The employee withdraws their own pending request.
     *
     * <p>This used to delete the row, which left no trace that the request had
     * ever been made and meant cancelled requests could not be counted. It now
     * records CANCELLED and keeps the record, so the history reads true and a
     * cancellation is visible to whoever it was waiting on. Nothing else changes:
     * still the owner only, still pending only.
     */
    @Transactional
    public void cancel(Long userId, Long id) {
        PermissionRequest p = repo.findById(id).orElseThrow(() -> ApiException.notFound("Permission request"));
        if (!p.getUserId().equals(userId)) throw ApiException.business("Not your request");
        if (!"PENDING".equals(p.getStatus())) throw ApiException.business("Only pending requests can be cancelled");
        p.setStatus("CANCELLED");
        p.setDecidedAt(LocalDateTime.now());
        repo.save(p);

        // Whoever it was waiting on should know it no longer is.
        if (p.getRequestedTo() != null) {
            String who = userRepository.findById(userId).map(User::getName).orElse("An employee");
            notificationService.createAndPush(p.getRequestedTo(),
                    "Permission request cancelled",
                    who + " withdrew their permission request for " + p.getRequestDate(),
                    "PERMISSION", "/leave/permissions");
        }
    }

    /** Admin: every permission request (read-only overview). */
    @Transactional(readOnly = true)
    public List<PermissionResponse> all() {
        return repo.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse).toList();
    }

    private PermissionResponse toResponse(PermissionRequest p) {
        User u = userRepository.findById(p.getUserId()).orElse(null);
        String toName = p.getRequestedTo() == null ? null
                : userRepository.findById(p.getRequestedTo()).map(User::getName).orElse(null);
        String decidedByName = p.getDecidedBy() == null ? null
                : userRepository.findById(p.getDecidedBy()).map(User::getName).orElse(null);
        return PermissionResponse.of(p,
                u != null ? u.getName() : "?",
                u != null ? u.getEmployeeCode() : "?",
                toName, decidedByName,
                u != null ? u.getDesignationTitle() : null);
    }
}
