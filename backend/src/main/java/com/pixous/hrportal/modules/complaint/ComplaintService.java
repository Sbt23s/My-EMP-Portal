package com.pixous.hrportal.modules.complaint;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.complaint.dto.ComplaintDecisionRequest;
import com.pixous.hrportal.modules.complaint.dto.ComplaintRequest;
import com.pixous.hrportal.modules.complaint.dto.ComplaintResponse;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Set;

/** Complaints / Needs: employees & managers submit; HR/Admin review and respond. */
@Service
@RequiredArgsConstructor
public class ComplaintService {

    private static final Set<String> VALID_KIND = Set.of("COMPLAINT", "NEED");
    private static final Set<String> VALID_PRIORITY = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> VALID_STATUS =
            Set.of("OPEN", "IN_REVIEW", "RESOLVED", "REJECTED");

    private final ComplaintNeedRepository repository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;
    private final com.pixous.hrportal.modules.notification.OversightNotifier oversight;

    /** Text a user if we have a usable mobile number for them. */
    private void sms(Long userId, String message) {
        if (userId == null) return;
        userRepository.findById(userId)
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + message));
    }

    @Transactional
    public ComplaintResponse submit(Long userId, ComplaintRequest req) {
        ComplaintNeed c = new ComplaintNeed();
        c.setReferenceCode(generateCode());
        c.setRaisedBy(userId);
        c.setKind(normalise(req.kind(), VALID_KIND, "COMPLAINT"));
        c.setCategory(blankToNull(req.category()));
        c.setSubject(req.subject().trim());
        c.setDescription(req.description().trim());
        c.setPriority(normalise(req.priority(), VALID_PRIORITY, "MEDIUM"));
        c.setStatus("OPEN");
        c.setRequestedTo(req.requestedTo());
        ComplaintNeed saved = repository.save(c);

        // Goes to the person it was addressed to; only when nobody was chosen
        // does it fall back to every HR/Admin.
        String submitter = safeName(userId);
        String label = "NEED".equals(saved.getKind()) ? "need" : "complaint";
        java.util.List<User> targets;
        if (saved.getRequestedTo() != null) {
            targets = userRepository.findById(saved.getRequestedTo())
                    .map(java.util.List::of).orElseGet(java.util.List::of);
        } else {
            // Nobody chosen — every HR and admin, deduplicated.
            java.util.Map<Long, User> everyone = new java.util.LinkedHashMap<>();
            userRepository.findByPermission("COMPLAINT_MANAGE").forEach(u -> everyone.put(u.getId(), u));
            userRepository.findByPermission("USER_MANAGE").forEach(u -> everyone.put(u.getId(), u));
            targets = java.util.List.copyOf(everyone.values());
        }

        // A copy to the CTO, whoever the complaint was addressed to. This is
        // deliberately the one place oversight reaches past the recipient: a
        // complaint about HR goes to the CTO, and one about anybody else
        // should still be visible to the person who has to act on a pattern.
        oversight.notifyCto(userId, "New " + label + ": " + saved.getReferenceCode(),
                submitter + " submitted a " + label, "COMPLAINT", "/complaints");

        targets.forEach(staff -> {
            if (!staff.getId().equals(userId)) {
                notificationService.createAndPush(staff.getId(),
                        "New " + label + ": " + saved.getReferenceCode(),
                        submitter + " submitted a " + label,
                        "COMPLAINT", "/complaints");
                sms(staff.getId(), submitter + " submitted a " + label + " ("
                        + saved.getReferenceCode() + "). Please review in the portal.");
            }
        });

        return toResponse(saved);
    }

    /**
     * People a complaint or need can be addressed to — HR and admins. Open to
     * every signed-in user, since the full directory is not theirs to read.
     * HR is reached through COMPLAINT_MANAGE; admins through USER_MANAGE.
     */
    /** Employee code of the one person HR's own complaints go to. */
    private static final String HR_COMPLAINT_APPROVER_CODE = "PIX-E100";

    private static boolean isHrRole(User u) {
        return u != null && u.getRoles().stream()
                .map(com.pixous.hrportal.modules.user.Role::getCode)
                .anyMatch(c -> "IT_MGR".equals(c) || "IT_HR".equals(c) || "CV_HR".equals(c));
    }

    /**
     * Who a complaint may be addressed to, which depends on who is raising it:
     *  - an employee or a Team Leader -> HR, and only HR. A complaint is a thing
     *    you tell HR; putting the admin in the list invites skipping them.
     *  - HR -> the one person above them, by employee code.
     * Nobody appears in their own list.
     */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> recipients(Long requesterId) {
        User me = requesterId == null ? null : userRepository.findById(requesterId).orElse(null);
        boolean iAmHr = isHrRole(me);

        java.util.Map<Long, java.util.Map<String, Object>> map = new java.util.LinkedHashMap<>();

        // 1. Always include CTO Elamaran Subramanian (PIX-E100)
        userRepository.findByEmployeeCode(HR_COMPLAINT_APPROVER_CODE).ifPresent(u -> {
            if (u.isEnabled() && !u.getId().equals(requesterId)) {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", u.getId());
                m.put("name", "CTO (" + u.getEmployeeCode() + ")");
                m.put("code", u.getEmployeeCode());
                m.put("role", "CTO");
                map.put(u.getId(), m);
            }
        });

        // 2. Always include single System Admin
        userRepository.findByPermission("USER_MANAGE").stream()
                .filter(User::isEnabled)
                .filter(u -> !u.getId().equals(requesterId))
                .filter(u -> !HR_COMPLAINT_APPROVER_CODE.equalsIgnoreCase(u.getEmployeeCode()))
                .findFirst()
                .ifPresent(u -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("name", "System Admin (" + u.getEmployeeCode() + ")");
                    m.put("code", u.getEmployeeCode());
                    m.put("role", "Admin");
                    map.putIfAbsent(u.getId(), m);
                });

        // 3. For Employees & TLs (not HR), also include single HR option
        if (!iAmHr) {
            userRepository.findByPermission("COMPLAINT_MANAGE").stream()
                    .filter(User::isEnabled)
                    .filter(u -> !u.getId().equals(requesterId))
                    .filter(ComplaintService::isHrRole)
                    .findFirst()
                    .ifPresent(u -> {
                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", u.getId());
                        m.put("name", "HR (" + u.getEmployeeCode() + ")");
                        m.put("code", u.getEmployeeCode());
                        m.put("role", "HR");
                        map.putIfAbsent(u.getId(), m);
                    });
        }

        return java.util.List.copyOf(map.values());
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> mySubmissions(Long userId, int page, int size) {
        Page<ComplaintNeed> result =
                repository.findByRaisedByOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(result.map(this::toResponse));
    }

    /**
     * The review queue, as the person asking is entitled to see it.
     *
     * <p>A complaint names who it is for. HR and the CTO are both offered as
     * recipients, and choosing between them is a real choice -- somebody
     * complaining about HR sends it to the CTO precisely so HR does not read
     * it. This returned every complaint to anyone holding COMPLAINT_MANAGE,
     * so it did.
     *
     * <p>A system administrator still sees everything: they hold USER_MANAGE
     * to keep the portal running, and a queue they cannot see is one they
     * cannot fix. Everybody else sees what was addressed to them, plus what
     * they raised themselves.
     */
    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> all(Long viewerId, String status, String kind, int page, int size) {
        String statusFilter = (status == null || status.isBlank()) ? null : status.toUpperCase();
        String kindFilter = (kind == null || kind.isBlank()) ? null : kind.toUpperCase();
        // Keyed on the account, not on USER_MANAGE -- HR holds that permission
        // for managing employee records, and checking it here handed them the
        // complaints addressed past them to the CTO.
        boolean seesEverything = oversight.seesEveryRequest(viewerId);
        Page<ComplaintNeed> result = seesEverything
                ? repository.filterAll(statusFilter, kindFilter, PageRequest.of(page, size))
                : repository.filterForViewer(statusFilter, kindFilter, viewerId,
                        PageRequest.of(page, size));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ComplaintResponse get(Long id) {
        return toResponse(find(id));
    }

    /**
     * Withdraw a complaint you raised, while nobody has acted on it.
     *
     * <p>The row stays and its status becomes CANCELLED rather than being
     * deleted: a reviewer who has already seen it should find out what became
     * of it instead of finding it gone.
     *
     * <p>Only the raiser, and only while it is still OPEN. Once HR has taken
     * it into review the handling is theirs, and withdrawing it out from
     * under them is not the raiser's to do.
     */
    @Transactional
    public void cancelOwn(Long userId, Long id) {
        ComplaintNeed c = find(id);
        if (!userId.equals(c.getRaisedBy())) {
            throw ApiException.business("You can only cancel complaints you raised");
        }
        if ("CANCELLED".equalsIgnoreCase(c.getStatus())) {
            throw ApiException.business("This complaint is already cancelled.");
        }
        if (!"OPEN".equalsIgnoreCase(c.getStatus())) {
            throw ApiException.business(
                    "This complaint is already " + c.getStatus().toLowerCase().replace('_', ' ')
                            + " — it can no longer be cancelled.");
        }
        c.setStatus("CANCELLED");
        repository.save(c);
    }

    @Transactional
    public ComplaintResponse respond(Long staffId, Long id, ComplaintDecisionRequest req) {
        ComplaintNeed c = find(id);
        String status = req.status() == null ? "" : req.status().toUpperCase();
        if (!VALID_STATUS.contains(status)) {
            throw ApiException.business("Invalid status: " + req.status());
        }
        c.setStatus(status);
        if (req.response() != null && !req.response().isBlank()) {
            c.setHrResponse(req.response().trim());
        }
        c.setHandledBy(staffId);
        if (status.equals("RESOLVED") || status.equals("REJECTED")) {
            c.setResolvedAt(LocalDateTime.now());
        } else {
            c.setResolvedAt(null);
        }
        c.setUpdatedAt(LocalDateTime.now());
        ComplaintNeed saved = repository.save(c);

        // Notify the original submitter of the update.
        notificationService.createAndPush(saved.getRaisedBy(),
                saved.getReferenceCode() + " updated",
                "Your submission is now " + status.toLowerCase().replace('_', ' '),
                "COMPLAINT", "/complaints");

        // And the outcome.
        oversight.notifyCto(staffId, saved.getReferenceCode() + " updated",
                safeName(saved.getRaisedBy()) + "'s submission is now "
                        + status.toLowerCase().replace('_', ' ') + ".",
                "COMPLAINT", "/complaints");

        sms(saved.getRaisedBy(), saved.getReferenceCode() + " is now "
                + status.toLowerCase().replace('_', ' ') + ".");

        return toResponse(saved);
    }

    // ---- helpers ----

    private ComplaintNeed find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Complaint/Need"));
    }

    private ComplaintResponse toResponse(ComplaintNeed c) {
        User raiser = c.getRaisedBy() == null ? null
                : userRepository.findById(c.getRaisedBy()).orElse(null);
        String raisedByName = raiser != null ? raiser.getName() : "User";
        String raisedByCode = raiser != null ? raiser.getEmployeeCode() : null;
        String handledByName = c.getHandledBy() == null ? null : safeName(c.getHandledBy());
        String requestedToName = c.getRequestedTo() == null ? null : safeName(c.getRequestedTo());
        String team = raiser != null ? raiser.getDesignationTitle() : null;
        return ComplaintResponse.from(c, raisedByName, raisedByCode, handledByName, requestedToName, team);
    }

    private String safeName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getName).orElse("User");
    }

    private String generateCode() {
        // Increment from the highest existing code for this year — count()+1 breaks
        // after any row is deleted (it regenerates an already-used code).
        String prefix = "CN-" + Year.now().getValue() + "-";
        String max = repository.findMaxReferenceCode(prefix);
        long next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Long.parseLong(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // Fall back to 1 if the suffix isn't numeric.
            }
        }
        return prefix + String.format("%05d", next);
    }

    private String normalise(String value, Set<String> allowed, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String upper = value.trim().toUpperCase();
        return allowed.contains(upper) ? upper : fallback;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
