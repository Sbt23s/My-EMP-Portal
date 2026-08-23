package com.pixous.hrportal.common;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sending mail out of the portal.
 *
 * <p>This is the only place that talks to the mail server, so there is one
 * place to look when mail is not arriving and one place to change when the
 * provider changes.
 *
 * <h2>On not being configured</h2>
 *
 * <p>The portal has never sent an email. The mail starter is on the classpath
 * and Spring will happily hand out a {@code JavaMailSender} pointed at
 * {@code localhost:1025} — the default for a developer's mail catcher — which
 * on the server is nothing at all. A send against it does not fail quickly and
 * cleanly; it waits for a connection that will never be accepted.
 *
 * <p>So configuration is checked before anything is attempted, and the absence
 * of it is reported as what it is: a setting nobody has filled in, not a
 * mysterious failure. Someone pressing "Send payslip" needs to be told which
 * of those two it was, because only one of them is their problem.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final String host;
    private final String from;

    public MailService(
            JavaMailSender sender,
            @Value("${spring.mail.host:}") String host,
            @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.host = host == null ? "" : host.trim();
        this.from = from == null ? "" : from.trim();
    }

    /**
     * Whether a real mail server has been configured.
     *
     * <p>localhost is treated as "not configured" on purpose. It is the
     * default, it is what a developer's catcher listens on, and in production
     * it means the setting was never filled in — silently dropping mail into
     * a socket nobody is listening to is worse than saying so.
     */
    public boolean isConfigured() {
        return !host.isBlank()
                && !host.equalsIgnoreCase("localhost")
                && !host.equals("127.0.0.1")
                && !from.isBlank();
    }

    /** What to tell somebody who tried to send mail with no mail server set up. */
    public String notConfiguredMessage() {
        return "Email has not been set up on this server yet, so nothing was sent. "
                + "Ask your administrator to configure the mail settings.";
    }

    /**
     * Send one message with one PDF attached.
     *
     * @throws ApiException when mail is not configured, or the server refuses it
     */
    public void sendWithPdf(String to, String subject, String bodyHtml,
                            String attachmentName, byte[] pdf) {
        if (!isConfigured()) {
            throw ApiException.business(notConfiguredMessage());
        }
        if (to == null || to.isBlank()) {
            throw ApiException.business("That employee has no email address on their profile.");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            // true: this message has an attachment, so it needs to be multipart.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(bodyHtml, true);
            helper.addAttachment(attachmentName, new ByteArrayResource(pdf), "application/pdf");
            sender.send(message);
            log.info("Sent '{}' to {}", subject, to);
        } catch (Exception e) {
            // The underlying text names hosts, ports and sometimes credentials,
            // so it goes to the log and not to the browser.
            log.error("Could not send '{}' to {}", subject, to, e);
            throw ApiException.business(
                    "The email could not be sent. The mail server refused it — "
                    + "the details are in the server log.");
        }
    }
}
