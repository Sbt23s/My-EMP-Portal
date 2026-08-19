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

    private final PermissionRequestRepository repo;
    private final UserRepository userRepository;
    private final com.pixous.hrportal.modules.notification.NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

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
            notificationService.createAndPush(req.requestedTo(),
                    "New permission request", detail, "PERMISSION", "/leave/permissions");
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

    /** Requests addressed to the given approver (only they see/act on them). */
    @Transactional(readOnly = true)
    public List<PermissionResponse> pendingFor(Long approverId) {
        return repo.findByStatusAndRequestedTo("PENDING", approverId)
                .stream().map(this::toResponse).toList();
    }

    /** Every request addressed to the approver (all statuses) — full details. */
    @Transactional(readOnly = true)
    public List<PermissionResponse> forApprover(Long approverId) {
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
            // HR permission request -> ONLY CTO Elamaran Subramanian (PIX-E100) or System Admin
            allowed = u -> "PIX-E100".equalsIgnoreCase(u.getEmployeeCode())
                    || hasRole(u, "SUPER_ADMIN") || hasRole(u, "COMPANY_ADMIN");
        } else if (iAmTl) {
            // TL permission request -> HR and CTO Elamaran Subramanian (PIX-E100)
            allowed = u -> "PIX-E100".equalsIgnoreCase(u.getEmployeeCode())
                    || hasRole(u, "IT_MGR") || hasRole(u, "IT_HR") || hasRole(u, "SUPER_ADMIN") || hasRole(u, "COMPANY_ADMIN");
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
        User decider = userRepository.findById(deciderId).orElseThrow(() -> ApiException.notFound("Decider"));
        boolean isDirectApprover = p.getRequestedTo() != null && p.getRequestedTo().equals(deciderId);
        boolean isSysAdmin = hasRole(decider, "SUPER_ADMIN") || hasRole(decider, "COMPANY_ADMIN");
        if (!isDirectApprover && !isSysAdmin) {
            throw ApiException.business("Only the assigned approver for this request can approve or reject it");
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
        userRepository.findById(p.getUserId())
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + detail));
        return toResponse(saved);
    }

    /** One of HIGH | MEDIUM | LOW; anything else reads as MEDIUM. */
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
