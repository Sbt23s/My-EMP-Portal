package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.common.MailService;
import com.pixous.hrportal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Emailing a report that the browser produced.
 *
 * <p>A sheet exported from a table is assembled in the browser, from the rows
 * the person is actually looking at — their filters, their date range, their
 * search. Rebuilding that on the server to email it would mean two exports
 * that drift apart, and the one that arrives by mail being subtly not the one
 * that was on screen. So the browser sends the file it already made.
 *
 * <h2>Why this is deliberately narrow</h2>
 *
 * <p>"Send this file to that address" is, in the wrong hands, a way to post
 * company data anywhere and a way to send mail that appears to come from the
 * company. So it is fenced in: only roles that can already see everyone's data
 * may call it, only spreadsheet types are accepted, there is a size ceiling,
 * and every send is logged with who sent what to whom. None of those stop a
 * determined administrator, and none are meant to — they mean an accident
 * leaves a trace and a misuse is not invisible.
 */
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
@Tag(name = "Mail", description = "Send an exported report by email")
public class MailAttachmentController {

    private static final Logger log = LoggerFactory.getLogger(MailAttachmentController.class);

    /** Ten megabytes. A spreadsheet of every claim ever filed is far below this. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * Deliberately not RFC 5322. That grammar accepts addresses no mail server
     * here will ever route, and rejecting a real address is worse than
     * accepting an odd one the server will bounce anyway.
     */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[A-Za-z]{2,}$");

    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv",
            "application/pdf"
    );

    private final MailService mailService;

    @PostMapping(value = "/send-report", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','CLAIM_APPROVE','DASHBOARD_EXEC','PAYROLL_VIEW')")
    @Operation(summary = "Email an exported report to a chosen address")
    public ApiResponse<Void> sendReport(
            @RequestParam("to") String to,
            @RequestParam("subject") String subject,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam("file") MultipartFile file) throws IOException {

        String recipient = to == null ? "" : to.trim();
        if (!EMAIL.matcher(recipient).matches()) {
            throw ApiException.business("That does not look like an email address.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.business("There is nothing to send — the report came through empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.business("That report is too large to email. Narrow the filters and try again.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!ALLOWED_TYPES.contains(type)) {
            throw ApiException.business("Only spreadsheets and PDFs can be emailed from here.");
        }

        String cleanSubject = (subject == null || subject.isBlank())
                ? "Report from Pixous HR Portal" : subject.trim();

        String note = (message == null || message.isBlank())
                ? "" : "<p>" + escapeHtml(message.trim()).replace("\n", "<br>") + "</p>";

        String body = note
                + "<p>The report is attached.</p>"
                + "<p style=\"color:#6b7280;font-size:12px\">"
                + "Sent from the Pixous HR portal. This attachment may contain "
                + "confidential company information.</p>";

        // Logged before the attempt, so a send that fails halfway is still
        // recorded as having been tried, by whom, and to where.
        log.info("User {} is emailing '{}' ({} bytes) to {}",
                SecurityUtils.currentUserId(), file.getOriginalFilename(), file.getSize(), recipient);

        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "report.xlsx" : file.getOriginalFilename();

        mailService.sendAttachment(recipient, cleanSubject, body, name, type, file.getBytes());
        return ApiResponse.message("Sent to " + recipient);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
