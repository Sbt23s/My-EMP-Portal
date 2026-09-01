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
    private final com.pixous.hrportal.common.MailService mailService;
    private final AppreciationPdfService pdfService;

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

    /**
     * The letter as a PDF, for whoever is entitled to read it.
     *
     * <p>Downloading as the named employee records it, the same as opening
     * does -- the point is that the issuer can see the letter landed.
     */
    @Transactional
    public byte[] pdf(Long viewerId, Long id) {
        AppreciationLetter a = find(id);
        boolean isSubject = a.getEmployeeId().equals(viewerId);
        if (!isSubject && !a.getIssuedBy().equals(viewerId) && !canIssue()) {
            throw ApiException.business("That letter is not yours to read.");
        }
        User employee = userRepository.findById(a.getEmployeeId()).orElse(null);
        byte[] out = pdfService.render(a,
                employee == null ? "" : employee.getName(),
                employee == null ? null : employee.getDesignationTitle(),
                nameOf(a.getIssuedBy()),
                userRepository.findById(a.getIssuedBy())
                        .map(User::getDesignationTitle).orElse(null));
        if (out == null) {
            throw ApiException.business("The letter could not be rendered as a PDF.");
        }
        if (isSubject && a.getDownloadedAt() == null) {
            a.setDownloadedAt(LocalDateTime.now());
            repository.save(a);
            notify(a.getIssuedBy(), "Appreciation letter downloaded",
                    nameOf(viewerId) + " downloaded " + a.getReferenceCode() + ".");
        }
        return out;
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
        /*
         * The letter goes by email as well as into the portal. It is the kind
         * of thing people forward and keep, and one that only ever lived
         * behind a login would mostly go unread.
         *
         * Never throws: the letter is saved and the employee has already been
         * notified, so an unreachable mail server must not undo either.
         */
        try {
            if (employee.getEmail() != null && !employee.getEmail().isBlank()) {
                sendWithLetter(a, employee, issuer,
                        "Appreciation Letter " + a.getReferenceCode() + " - Pixous Technologies",
                        "<p>Dear " + employee.getName() + ",</p>"
                                + "<p>" + issuer + " has issued an appreciation letter to you "
                                + "in recognition of <b>" + escape(a.getAchievement()) + "</b>.</p>"
                                + "<blockquote>" + escape(a.getMessage()).replace("\n", "<br/>")
                                + "</blockquote>"
                                + "<p>You can read and download the full letter in the "
                                + "employee portal.</p>"
                                + "<p>Congratulations,<br/>HR Team<br/>Pixous Technologies</p>");
            }
        } catch (Exception e) {
            log.warn("Appreciation email failed: {}", e.getMessage());
        }
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

    /**
     * The letter by email, with the letter attached.
     *
     * <p>An appreciation is a thing people keep and forward, and one that
     * exists only inside a portal mostly does not get either. The PDF is the
     * same letter the page shows.
     *
     * <p>If the PDF cannot be rendered the mail still goes, carrying the
     * letter in its body: an attachment that failed is not a reason to send
     * nothing.
     */
    private void sendWithLetter(AppreciationLetter a, User employee, String issuer,
                                String subject, String bodyHtml) {
        byte[] pdf = null;
        try {
            pdf = pdfService.render(a, employee.getName(),
                    employee.getDesignationTitle(), issuer,
                    userRepository.findById(a.getIssuedBy())
                            .map(User::getDesignationTitle).orElse(null));
        } catch (Exception e) {
            log.warn("Appreciation PDF failed for {}: {}", a.getReferenceCode(), e.getMessage());
        }

        if (pdf != null && pdf.length > 0) {
            try {
                mailService.sendAttachment(employee.getEmail(), subject, bodyHtml,
                        "Appreciation_" + a.getReferenceCode() + ".pdf",
                        "application/pdf", pdf);
                return;
            } catch (Exception e) {
                log.warn("Appreciation mail with attachment failed, falling back: {}",
                        e.getMessage());
            }
        }
        mailService.trySend(employee.getEmail(), subject, bodyHtml);
    }

    /** Keeps a quoted message from breaking the surrounding HTML. */
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
