package com.pixous.hrportal.modules.appreciation;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.appreciation.dto.AppreciationDtos;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

/**
 * Appreciation letters.
 *
 * <p>HR, an administrator or the CTO writes one; the named employee receives
 * it and nobody else does. The reading rule is the whole of the security here:
 * an appreciation is a private thing between the company and one person, and
 * an employee reading a colleague's by guessing an id is the case this
 * refuses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppreciationService {

    private final AppreciationLetterRepository repository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    // ------------------------------------------------------------------ write

    @Transactional
    public AppreciationDtos.View create(Long issuerId, AppreciationDtos.CreateRequest req) {
        User employee = userRepository.findById(req.employeeId())
                .orElseThrow(() -> ApiException.notFound("Employee"));

        AppreciationLetter a = new AppreciationLetter();
        a.setReferenceCode(generateCode());
        a.setEmployeeId(employee.getId());
        a.setIssuedBy(issuerId);
        a.setLetterDate(req.letterDate());
        a.setAchievement(req.achievement().trim());
        a.setMessage(req.message().trim());
        a.setTemplate(req.template() == null || req.template().isBlank()
                ? "CLASSIC" : req.template().trim().toUpperCase());

        boolean send = Boolean.TRUE.equals(req.send());
        a.setStatus(send ? "SENT" : "DRAFT");
        AppreciationLetter saved = repository.save(a);

        // A draft is not yet a letter, so nobody is told about one.
        if (send) notifyIssued(saved, employee, nameOf(issuerId));

        return toView(saved);
    }

    /** Send a letter that was saved as a draft. */
    @Transactional
    public AppreciationDtos.View send(Long actorId, Long id) {
        AppreciationLetter a = find(id);
        if ("SENT".equals(a.getStatus())) {
            throw ApiException.business("This letter has already been sent.");
        }
        a.setStatus("SENT");
        a.setUpdatedAt(LocalDateTime.now());
        AppreciationLetter saved = repository.save(a);

        User employee = userRepository.findById(saved.getEmployeeId()).orElse(null);
        if (employee != null) notifyIssued(saved, employee, nameOf(actorId));
        return toView(saved);
    }

    /**
     * Delete a letter.
     *
     * <p>Only a draft. A sent one has been read by the person it praises, and
     * withdrawing an appreciation after the fact is not a thing the product
     * should make easy.
     */
    @Transactional
    public void delete(Long id) {
        AppreciationLetter a = find(id);
        if ("SENT".equals(a.getStatus())) {
            throw ApiException.business(
                    "A letter that has been sent cannot be deleted. The employee has already seen it.");
        }
        repository.delete(a);
    }

    // ------------------------------------------------------------------- read

    /** Everything, for whoever issues letters. */
    @Transactional(readOnly = true)
    public List<AppreciationDtos.View> all() {
        return repository.findAllByOrderByLetterDateDescIdDesc()
                .stream().map(this::toView).toList();
    }

    /** One employee's letters. Sent ones only -- a draft is not yet a letter. */
    @Transactional(readOnly = true)
    public List<AppreciationDtos.View> mine(Long employeeId) {
        return repository.findSentFor(employeeId).stream().map(this::toView).toList();
    }

    /**
     * One letter, if it is this person's to read.
     *
     * <p>Opening it as the named employee records that it was read, which is
     * how the issuer sees the letter landed. Reading it as its issuer does not
     * -- writing a letter and then opening it should not mark it viewed.
     */
    @Transactional
    public AppreciationDtos.View get(Long viewerId, Long id) {
        AppreciationLetter a = find(id);
        boolean isSubject = a.getEmployeeId().equals(viewerId);
        boolean allowed = isSubject || a.getIssuedBy().equals(viewerId) || canIssue();
        if (!allowed) {
            throw ApiException.business("That letter is not yours to read.");
        }
        if (isSubject && "SENT".equals(a.getStatus()) && a.getViewedAt() == null) {
            a.setViewedAt(LocalDateTime.now());
            repository.save(a);
            notify(a.getIssuedBy(), "Appreciation letter viewed",
                    nameOf(viewerId) + " opened " + a.getReferenceCode() + ".");
        }
        return toView(a);
    }

    /** Record that the employee downloaded it, and tell whoever wrote it. */
    @Transactional
    public void markDownloaded(Long viewerId, Long id) {
        AppreciationLetter a = find(id);
        if (!a.getEmployeeId().equals(viewerId)) return;
        if (a.getDownloadedAt() != null) return;
        a.setDownloadedAt(LocalDateTime.now());
        repository.save(a);
        notify(a.getIssuedBy(), "Appreciation letter downloaded",
                nameOf(viewerId) + " downloaded " + a.getReferenceCode() + ".");
    }

    // ---------------------------------------------------------------- helpers

    private AppreciationLetter find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Appreciation letter"));
    }

    private boolean canIssue() {
        return com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("COMPLAINT_MANAGE")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("DASHBOARD_EXEC");
    }

    private void notifyIssued(AppreciationLetter a, User employee, String issuer) {
        notify(employee.getId(), "Appreciation letter received",
                issuer + " has issued an appreciation letter to you: " + a.getAchievement() + ".");
        try {
            if (employee.getPhone() != null && !employee.getPhone().isBlank()) {
                smsService.send(employee.getPhone(),
                        "Pixous HR: an appreciation letter (" + a.getReferenceCode()
                                + ") is waiting in the portal.");
            }
        } catch (Exception e) {
            log.warn("Appreciation SMS failed: {}", e.getMessage());
        }
    }

    /** Never lets a notification failure lose the letter that was saved. */
    private void notify(Long userId, String title, String body) {
        if (userId == null) return;
        try {
            notificationService.createAndPush(userId, title, body, "APPRECIATION", "/appreciation");
        } catch (Exception e) {
            log.warn("Appreciation notification failed for {}: {}", userId, e.getMessage());
        }
    }

    private AppreciationDtos.View toView(AppreciationLetter a) {
        User employee = userRepository.findById(a.getEmployeeId()).orElse(null);
        User issuer = userRepository.findById(a.getIssuedBy()).orElse(null);
        return AppreciationDtos.View.of(
                a,
                employee != null ? employee.getName() : "Unknown",
                employee != null ? employee.getEmployeeCode() : null,
                employee != null ? employee.getDesignationTitle() : null,
                employee != null ? employee.getDesignationTitle() : null,
                issuer != null ? issuer.getName() : "Someone",
                issuer != null ? issuer.getDesignationTitle() : null
        );
    }

    private String nameOf(Long userId) {
        if (userId == null) return "Someone";
        return userRepository.findById(userId).map(User::getName).orElse("Someone");
    }

    private String generateCode() {
        // Counts up from the highest existing code rather than from the row
        // count: count()+1 regenerates a used code after any deletion, and the
        // column is unique, so the insert would fail.
        String prefix = "AL-" + Year.now().getValue() + "-";
        String max = repository.findMaxReferenceCode(prefix);
        long next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Long.parseLong(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // Fall back to 1 if the suffix is not numeric.
            }
        }
        return prefix + String.format("%05d", next);
    }
}
