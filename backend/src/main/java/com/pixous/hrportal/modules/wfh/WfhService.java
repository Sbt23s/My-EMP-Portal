package com.pixous.hrportal.modules.wfh;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.WorkCalendar;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.org.Holiday;
import com.pixous.hrportal.modules.org.HolidayRepository;
import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.modules.wfh.dto.WfhDtos;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Work From Home requests: applying, routing, deciding and reporting.
 *
 * <h2>The ladder</h2>
 *
 * <pre>
 *   employee    ->  their Team Leader
 *   Team Leader ->  HR
 *   HR          ->  the CTO
 * </pre>
 *
 * <p>One rung, and only one. The approver is resolved on the server from the
 * applicant's own role rather than taken from the request, so a client that
 * sends nothing still routes correctly and a client that sends the wrong
 * person is corrected rather than obeyed.
 *
 * <p>The rungs deliberately match {@code LeaveService.leaveApprovers} — the
 * same role codes, the same "one rung up" rule — because two chains that
 * disagree is how a request ends up with somebody who does not expect it. WFH
 * has no day-count branch: leave sends four days to HR instead of a Team
 * Leader because it is an absence to be covered, and a WFH day is not.
 */
@Service
@RequiredArgsConstructor
public class WfhService {

    /** The company head, identified by code as the rest of the system does. */
    private static final String CTO_CODE = "PIX-E100";

    private final WfhRequestRepository repository;
    private final UserRepository userRepository;
    private final HolidayRepository holidayRepository;
    private final NotificationService notificationService;

    // ----------------------------------------------------------- applying --

    @Transactional
    public WfhDtos.WfhView apply(Long userId, WfhDtos.ApplyRequest req) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        if (req.toDate().isBefore(req.fromDate())) {
            throw ApiException.business("The end date cannot be before the start date.");
        }

        /*
         * A weekend is already not a working day, so working from home on one
         * is not a thing to request. Said plainly, naming the day, rather than
         * accepted and then counted as zero.
         */
        if (WorkCalendar.isWeekend(req.fromDate())) {
            throw ApiException.business("Work from home cannot start on a "
                    + dayName(req.fromDate()) + ". Choose a weekday.");
        }
        if (WorkCalendar.isWeekend(req.toDate())) {
            throw ApiException.business("Work from home cannot end on a "
                    + dayName(req.toDate()) + ". Choose a weekday.");
        }

        long days = countWorkingDays(req.fromDate(), req.toDate());
        if (days <= 0) {
            throw ApiException.business(
                    "That range has no working days in it — every day in it is a "
                            + "weekend or a public holiday.");
        }

        /*
         * One request per person per day, as leave and permission have.
         *
         * Two overlapping WFH requests are two answers to one question, and if
         * both are approved the status board shows the same person twice.
         */
        List<WfhRequest> clash = repository.findOverlapping(
                userId, req.fromDate(), req.toDate());
        if (!clash.isEmpty()) {
            WfhRequest first = clash.get(0);
            String when = first.getFromDate().equals(first.getToDate())
                    ? "on " + first.getFromDate()
                    : "from " + first.getFromDate() + " to " + first.getToDate();
            throw ApiException.business(
                    "You already have a work from home request " + when
                            + " (" + first.getStatus().toLowerCase() + "). "
                            + "Cancel that one first, or choose other dates.");
        }

        // The rung, decided here rather than trusted from the payload.
        User approver = resolveApprover(me);
        if (approver == null) {
            throw ApiException.business(
                    "There is nobody set up to approve your work from home requests yet. "
                            + "Ask HR to assign an approver.");
        }

        WfhRequest r = new WfhRequest();
        r.setUserId(userId);
        r.setCompanyId(me.getCompanyId());
        r.setFromDate(req.fromDate());
        r.setToDate(req.toDate());
        r.setWorkingDays(BigDecimal.valueOf(days));
        r.setReason(trimToNull(req.reason()));
        r.setRemarks(trimToNull(req.remarks()));
        r.setStatus(WfhRequest.PENDING);
        r.setRequestedTo(approver.getId());
        WfhRequest saved = repository.save(r);

        notify(approver.getId(),
                "Work from home request pending",
                me.getName() + " asked to work from home "
                        + describe(saved.getFromDate(), saved.getToDate()));

        return toView(saved, userId);
    }

    // ----------------------------------------------------------- deciding --

    @Transactional
    public WfhDtos.WfhView decide(Long deciderId, Long id, WfhDtos.DecisionRequest req) {
        WfhRequest r = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Work from home request"));

        if (!r.isPending()) {
            throw ApiException.business(
                    "That request has already been " + r.getStatus().toLowerCase() + ".");
        }
        if (!canDecide(r, deciderId)) {
            throw ApiException.business("That request is not yours to decide.");
        }

        boolean approve = Boolean.TRUE.equals(req.approve());
        String comment = trimToNull(req.comment());
        if (!approve && comment == null) {
            // The applicant is owed a reason. An approval may be silent.
            throw ApiException.business("Give a reason for rejecting this request.");
        }

        r.setStatus(approve ? WfhRequest.APPROVED : WfhRequest.REJECTED);
        r.setDecidedBy(deciderId);
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionComment(comment);
        r.setUpdatedAt(LocalDateTime.now());
        WfhRequest saved = repository.save(r);

        String who = userRepository.findById(deciderId).map(User::getName).orElse("Your approver");
        notify(saved.getUserId(),
                approve ? "Work from home approved" : "Work from home rejected",
                who + " " + (approve ? "approved" : "rejected") + " your request for "
                        + describe(saved.getFromDate(), saved.getToDate())
                        + (comment == null ? "" : " — " + comment));

        return toView(saved, deciderId);
    }

    @Transactional
    public WfhDtos.WfhView cancel(Long userId, Long id) {
        WfhRequest r = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Work from home request"));

        if (!r.getUserId().equals(userId)) {
            throw ApiException.business("You can only cancel a request you raised.");
        }
        if (!r.isPending()) {
            throw ApiException.business(
                    "That request has already been " + r.getStatus().toLowerCase()
                            + " and cannot be withdrawn.");
        }

        r.setStatus(WfhRequest.CANCELLED);
        r.setUpdatedAt(LocalDateTime.now());
        WfhRequest saved = repository.save(r);

        if (saved.getRequestedTo() != null) {
            String who = userRepository.findById(userId).map(User::getName).orElse("An employee");
            notify(saved.getRequestedTo(),
                    "Work from home request withdrawn",
                    who + " withdrew their request for "
                            + describe(saved.getFromDate(), saved.getToDate()));
        }
        return toView(saved, userId);
    }

    // ------------------------------------------------------------ reading --

    @Transactional(readOnly = true)
    public List<WfhDtos.WfhView> mine(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(r -> toView(r, userId)).toList();
    }

    /** Addressed to me, whatever state it is in. */
    @Transactional(readOnly = true)
    public List<WfhDtos.WfhView> forMe(Long userId) {
        return repository.findByRequestedToOrderByCreatedAtDesc(userId)
                .stream().map(r -> toView(r, userId)).toList();
    }

    /**
     * Everything, for HR, the CTO and the administrators.
     *
     * <p>Gated on the controller. A Team Leader sees their own inbox through
     * {@link #forMe}; the whole organisation is not theirs to read.
     */
    @Transactional(readOnly = true)
    public List<WfhDtos.WfhView> all(Long viewerId) {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream().map(r -> toView(r, viewerId)).toList();
    }

    /** Who is working from home on a given day — the status board. */
    @Transactional(readOnly = true)
    public List<WfhDtos.WfhView> activeOn(LocalDate day, Long viewerId) {
        return repository.findActiveOn(day == null ? LocalDate.now() : day)
                .stream().map(r -> toView(r, viewerId)).toList();
    }

    /** Who a request from this person would go to, so the form can name them. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> approvers(Long userId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        User approver = resolveApprover(me);
        if (approver == null) return List.of();

        Map<String, Object> one = new LinkedHashMap<>();
        one.put("id", approver.getId());
        one.put("name", approver.getName());
        one.put("code", approver.getEmployeeCode());
        one.put("role", rungOf(approver));
        return List.of(one);
    }

    // ---------------------------------------------------------- internals --

    private boolean isCto(User u) {
        return CTO_CODE.equalsIgnoreCase(u.getEmployeeCode());
    }

    private boolean hasRole(User u, String... codes) {
        if (u == null || u.getRoles() == null) return false;
        Set<String> want = Set.of(codes);
        return u.getRoles().stream().map(Role::getCode).anyMatch(want::contains);
    }

    private boolean isHr(User u) {
        return hasRole(u, "IT_HR", "CV_HR", "IT_MGR");
    }

    private boolean isTl(User u) {
        return hasRole(u, "IT_TL", "CV_SUP");
    }

    /**
     * The rung a person sits on, for display.
     *
     * <p>The CTO is identified by employee code rather than by role, because
     * that account carries an employee role as well as its administrative
     * ones — reading the role list alone would file the company head as an
     * employee. The rest of this system identifies it the same way.
     */
    private String rungOf(User u) {
        if (u == null) return "Employee";
        if (isCto(u)) return "CTO";
        if (hasRole(u, "SUPER_ADMIN", "COMPANY_ADMIN", "BOARD_ADMIN")) return "System Admin";
        if (isHr(u)) return "HR";
        if (isTl(u)) return "Team Leader";
        return "Employee";
    }

    /**
     * One rung up, and only one.
     *
     * <p>Prefers somebody on the applicant's own team where the rung has more
     * than one candidate — a Team Leader who does not know the person cannot
     * judge whether the team can spare them. Falls back to the rung at large
     * rather than to nobody: an empty result is a form that cannot be
     * submitted, which is worse than the wrong Team Leader approving.
     */
    private User resolveApprover(User me) {
        Predicate<User> rung;
        if (isHr(me) || hasRole(me, "SUPER_ADMIN", "COMPANY_ADMIN", "BOARD_ADMIN")) {
            rung = this::isCto;                       // HR and admins -> the CTO
        } else if (isTl(me)) {
            rung = u -> isHr(u) && !isCto(u);         // Team Leader -> HR
        } else {
            rung = u -> isTl(u) && !isCto(u);         // employee -> Team Leader
        }

        List<User> pool = userRepository.findByEnabledTrue().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(rung)
                .toList();
        if (pool.isEmpty()) {
            // Nobody on the rung above. HR is the sensible catch-all: somebody
            // has to be able to answer, and HR can always route it onwards.
            pool = userRepository.findByEnabledTrue().stream()
                    .filter(u -> !u.getId().equals(me.getId()))
                    .filter(u -> isHr(u) && !isCto(u))
                    .toList();
        }
        if (pool.isEmpty()) return null;

        /*
         * Same team first, where the rung has more than one candidate.
         *
         * "Team" is designationTitle, which is what LeaveService compares for
         * the same purpose -- two chains disagreeing about what a team is would
         * send leave and WFH to different Team Leaders for one person.
         */
        String myTeam = me.getDesignationTitle();
        if (myTeam != null && !myTeam.isBlank()) {
            Optional<User> sameTeam = pool.stream()
                    .filter(u -> myTeam.trim().equalsIgnoreCase(
                            u.getDesignationTitle() == null ? "" : u.getDesignationTitle().trim()))
                    .findFirst();
            if (sameTeam.isPresent()) return sameTeam.get();
        }
        return pool.get(0);
    }

    /**
     * Whether this person may decide this request.
     *
     * <p>The person it names, and never its author. An administrator is not
     * given a blanket override here: the chain exists so a request reaches
     * somebody who can judge it, and an override quietly makes the chain
     * optional.
     */
    private boolean canDecide(WfhRequest r, Long viewerId) {
        return r.isPending()
                && viewerId != null
                && viewerId.equals(r.getRequestedTo())
                && !viewerId.equals(r.getUserId());
    }

    private long countWorkingDays(LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = holidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAsc(from, to).stream()
                .map(Holiday::getHolidayDate).collect(Collectors.toSet());
        long days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (WorkCalendar.isWeekend(d)) continue;
            if (holidays.contains(d)) continue;
            days++;
        }
        return days;
    }

    /** Never lets a notification failure lose the thing that was saved. */
    private void notify(Long userId, String title, String body) {
        try {
            notificationService.createAndPush(userId, title, body, "WFH", "/leave/wfh");
        } catch (Exception ignored) {
            // The request stands either way, and that is what mattered.
        }
    }

    private static String describe(LocalDate from, LocalDate to) {
        return from.equals(to) ? "on " + from : "from " + from + " to " + to;
    }

    private static String dayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, Locale.ENGLISH);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private WfhDtos.WfhView toView(WfhRequest r, Long viewerId) {
        User applicant = userRepository.findById(r.getUserId()).orElse(null);
        User approver = r.getRequestedTo() == null ? null
                : userRepository.findById(r.getRequestedTo()).orElse(null);
        User decider = r.getDecidedBy() == null ? null
                : userRepository.findById(r.getDecidedBy()).orElse(null);

        // COMPLETED is shown, not stored: an approved request whose last day
        // has passed. Deriving it means it is never stale.
        String status = r.isCompleted(LocalDate.now()) ? "COMPLETED" : r.getStatus();

        return new WfhDtos.WfhView(
                r.getId(),
                r.getUserId(),
                applicant != null ? applicant.getName() : "Employee",
                applicant != null ? applicant.getEmployeeCode() : null,
                applicant != null ? applicant.getDepartmentTitle() : null,
                applicant != null ? applicant.getDesignationTitle() : null,
                rungOf(applicant),
                r.getFromDate(),
                r.getToDate(),
                r.getWorkingDays(),
                r.getReason(),
                r.getRemarks(),
                status,
                r.getRequestedTo(),
                approver != null ? approver.getName() : null,
                approver != null ? rungOf(approver) : null,
                r.getDecidedBy(),
                decider != null ? decider.getName() : null,
                r.getDecidedAt(),
                r.getDecisionComment(),
                r.getCreatedAt(),
                canDecide(r, viewerId),
                r.isPending() && Objects.equals(viewerId, r.getUserId()));
    }
}
