package com.pixous.hrportal.modules.discipline;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.discipline.dto.DisciplineDtos;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.notification.OversightNotifier;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Set;

/**
 * Disciplinary records: raised by HR, reviewed by the CTO, answered by the
 * employee.
 *
 * <p>Three people see one record and each sees a different part of it. HR
 * writes what happened; the employee reads it and may answer; the CTO reads
 * both and writes the remark the employee is then shown. The rules below say
 * who may do which, and they live here rather than in the page, because the
 * page can only decide what to draw.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisciplineService {

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES =
            Set.of("OPEN", "UNDER_REVIEW", "RESOLVED", "CLOSED", "CANCELLED");

    private final DisciplineRecordRepository repository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final OversightNotifier oversight;
    private final com.pixous.hrportal.common.SmsService smsService;

    // ------------------------------------------------------------------ create

    @Transactional
    public DisciplineDtos.View create(Long reporterId, DisciplineDtos.CreateRequest req) {
        /*
         * Raising is HR's. The CTO reviews what HR raised, and holds
         * USER_MANAGE like HR does -- so the permission alone cannot separate
         * them and the page's buttons cannot be the rule.
         */
        if (oversight.seesEveryRequest(reporterId)) {
            throw ApiException.business(
                    "Discipline records are raised by HR. Yours is the review.");
        }

        User employee = userRepository.findById(req.employeeId())
                .orElseThrow(() -> ApiException.notFound("Employee"));

        /*
         * Nobody raises a record about themselves. It is not something anybody
         * means to do, and it would put a record on a file that its subject
         * wrote and can answer.
         */
        if (employee.getId().equals(reporterId)) {
            throw ApiException.business("A discipline record cannot be raised about yourself.");
        }

        DisciplineRecord d = new DisciplineRecord();
        d.setReferenceCode(generateCode());
        d.setEmployeeId(employee.getId());
        d.setReportedBy(reporterId);
        d.setIncidentDate(req.incidentDate());
        d.setDisciplineType(req.disciplineType().trim());
        d.setSeverity(normalise(req.severity(), SEVERITIES, "MEDIUM"));
        d.setSubject(req.subject().trim());
        d.setDescription(req.description().trim());
        d.setActionTaken(trimToNull(req.actionTaken()));
        d.setAttachments(trimToNull(req.attachments()));
        d.setStatus("OPEN");
        DisciplineRecord saved = repository.save(d);

        String reporter = nameOf(reporterId);
        notifyEmployeeRaised(saved, employee, reporter);
        notifyCtoRaised(saved, employee, reporter);

        return toView(saved);
    }

    // ------------------------------------------------------------------- reads

    /** Everything, for HR and the CTO. */
    @Transactional(readOnly = true)
    public List<DisciplineDtos.View> all(String status, int page, int size) {
        String filter = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? null : status.toUpperCase();
        return repository.filterAll(filter, PageRequest.of(page, size))
                .map(this::toView).getContent();
    }

    /** The records about one employee. Nobody sees another employee's. */
    @Transactional(readOnly = true)
    public List<DisciplineDtos.View> mine(Long employeeId) {
        return repository.findByEmployeeIdOrderByIncidentDateDescIdDesc(employeeId)
                .stream().map(this::toView).toList();
    }

    /** What the CTO still has to look at. */
    @Transactional(readOnly = true)
    public List<DisciplineDtos.View> pendingReview() {
        return repository.findPendingReview().stream().map(this::toView).toList();
    }

    /**
     * One record, if this person is entitled to it.
     *
     * <p>The employee it is about, whoever raised it, and anyone who oversees
     * the queue. Reading a colleague's record by guessing an id is what this
     * refuses.
     */
    @Transactional(readOnly = true)
    public DisciplineDtos.View get(Long viewerId, Long id) {
        DisciplineRecord d = find(id);
        boolean allowed = d.getEmployeeId().equals(viewerId)
                || d.getReportedBy().equals(viewerId)
                || canManage()
                || oversight.seesEveryRequest(viewerId);
        if (!allowed) {
            throw ApiException.business("That record is not yours to read.");
        }
        return toView(d);
    }

    // ------------------------------------------------------------------ update

    /**
     * HR corrects a record.
     *
     * <p>The employee it concerns is deliberately not editable: moving a record
     * to somebody else rewrites history rather than correcting it, and the
     * first employee has already been told about it.
     */
    @Transactional
    public DisciplineDtos.View update(Long actorId, Long id, DisciplineDtos.UpdateRequest req) {
        // As with create: the CTO reviews rather than manages.
        if (oversight.seesEveryRequest(actorId)) {
            throw ApiException.business(
                    "A discipline record is edited by HR. Yours is the review.");
        }
        DisciplineRecord d = find(id);
        if ("CLOSED".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus())) {
            throw ApiException.business(
                    "This record is " + d.getStatus().toLowerCase() + " and can no longer be edited.");
        }
        d.setIncidentDate(req.incidentDate());
        d.setDisciplineType(req.disciplineType().trim());
        d.setSeverity(normalise(req.severity(), SEVERITIES, d.getSeverity()));
        d.setSubject(req.subject().trim());
        d.setDescription(req.description().trim());
        d.setActionTaken(trimToNull(req.actionTaken()));
        if (req.attachments() != null) d.setAttachments(trimToNull(req.attachments()));
        if (req.status() != null && !req.status().isBlank()) {
            d.setStatus(normalise(req.status(), STATUSES, d.getStatus()));
        }
        d.setUpdatedAt(LocalDateTime.now());
        DisciplineRecord saved = repository.save(d);

        notify(saved.getEmployeeId(), "Discipline record updated",
                saved.getReferenceCode() + " was updated by " + nameOf(actorId) + ".");
        return toView(saved);
    }

    /**
     * Withdraw a record.
     *
     * <p>Cancelled rather than deleted: one that was raised and then withdrawn
     * is a thing that happened, the employee was already told about it, and
     * having it vanish reads worse than having it marked withdrawn.
     */
    @Transactional
    public void cancel(Long actorId, Long id) {
        // As with create: the CTO reviews rather than manages.
        if (oversight.seesEveryRequest(actorId)) {
            throw ApiException.business(
                    "A discipline record is withdrawn by HR. Yours is the review.");
        }
        DisciplineRecord d = find(id);
        if ("CANCELLED".equals(d.getStatus())) {
            throw ApiException.business("This record is already cancelled.");
        }
        if ("CLOSED".equals(d.getStatus())) {
            throw ApiException.business("A closed record can no longer be cancelled.");
        }
        d.setStatus("CANCELLED");
        d.setUpdatedAt(LocalDateTime.now());
        repository.save(d);

        notify(d.getEmployeeId(), "Discipline record withdrawn",
                d.getReferenceCode() + " was withdrawn by " + nameOf(actorId) + ".");
        oversight.notifyCto(actorId, "Discipline record withdrawn",
                d.getReferenceCode() + " for " + nameOf(d.getEmployeeId())
                        + " was withdrawn by " + nameOf(actorId) + ".",
                "DISCIPLINE", "/discipline");
    }

    // ---------------------------------------------------------------- employee

    /** The employee's answer. Theirs alone, and only while the record stands. */
    @Transactional
    public DisciplineDtos.View respond(Long employeeId, Long id, DisciplineDtos.ResponseRequest req) {
        DisciplineRecord d = find(id);
        if (!d.getEmployeeId().equals(employeeId)) {
            throw ApiException.business("You can only respond to a record about yourself.");
        }
        if ("CANCELLED".equals(d.getStatus())) {
            throw ApiException.business("This record was withdrawn, so there is nothing to answer.");
        }
        d.setEmployeeResponse(req.response().trim());
        d.setRespondedAt(LocalDateTime.now());
        // An answered record waits on the CTO rather than on the employee.
        if ("OPEN".equals(d.getStatus())) d.setStatus("UNDER_REVIEW");
        d.setUpdatedAt(LocalDateTime.now());
        DisciplineRecord saved = repository.save(d);

        String who = nameOf(employeeId);
        notify(saved.getReportedBy(), "Response to " + saved.getReferenceCode(),
                who + " responded to the discipline record.");
        oversight.notifyCto(employeeId, "Response to " + saved.getReferenceCode(),
                who + " responded to their discipline record.", "DISCIPLINE", "/discipline");
        return toView(saved);
    }

    // --------------------------------------------------------------------- CTO

    /**
     * The CTO's review. The remark is the warning the employee is shown, so
     * writing one tells them.
     */
    @Transactional
    public DisciplineDtos.View review(Long ctoId, Long id, DisciplineDtos.ReviewRequest req) {
        DisciplineRecord d = find(id);
        if ("CANCELLED".equals(d.getStatus())) {
            throw ApiException.business("This record was withdrawn and cannot be reviewed.");
        }
        // Closing is the end of it. Writing into a closed record would reopen
        // a decision and notify the employee about something already settled.
        if ("CLOSED".equals(d.getStatus())) {
            throw ApiException.business("This record is closed. Reopen it before reviewing again.");
        }
        String remarks = trimToNull(req.remarks());
        if (remarks != null) {
            d.setCtoRemarks(remarks);
            d.setReviewedBy(ctoId);
            d.setReviewedAt(LocalDateTime.now());
        }
        if (req.status() != null && !req.status().isBlank()) {
            d.setStatus(normalise(req.status(), STATUSES, d.getStatus()));
        }
        d.setUpdatedAt(LocalDateTime.now());
        DisciplineRecord saved = repository.save(d);

        if (remarks != null) {
            notify(saved.getEmployeeId(), "Message from the CTO on " + saved.getReferenceCode(),
                    remarks.length() > 160 ? remarks.substring(0, 157) + "..." : remarks);
            sms(saved.getEmployeeId(), "Pixous HR: a message about discipline record "
                    + saved.getReferenceCode() + " is waiting in the portal.");
        }
        notify(saved.getReportedBy(), saved.getReferenceCode() + " reviewed",
                "The CTO reviewed this record. It is now "
                        + saved.getStatus().toLowerCase().replace('_', ' ') + ".");
        return toView(saved);
    }

    // ----------------------------------------------------------------- helpers

    private DisciplineRecord find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Discipline record"));
    }

    private boolean canManage() {
        return com.pixous.hrportal.security.SecurityUtils.hasAuthority("USER_MANAGE")
                || com.pixous.hrportal.security.SecurityUtils.hasAuthority("COMPLAINT_MANAGE");
    }

    private DisciplineDtos.View toView(DisciplineRecord d) {
        User employee = userRepository.findById(d.getEmployeeId()).orElse(null);
        return DisciplineDtos.View.of(
                d,
                employee != null ? employee.getName() : "Unknown",
                employee != null ? employee.getEmployeeCode() : null,
                employee != null ? employee.getDesignationTitle() : null,
                nameOf(d.getReportedBy()),
                d.getReviewedBy() == null ? null : nameOf(d.getReviewedBy())
        );
    }

    private void notifyEmployeeRaised(DisciplineRecord d, User employee, String reporter) {
        notify(employee.getId(), "New discipline record " + d.getReferenceCode(),
                "A discipline record has been created for you by " + reporter
                        + ". Subject: " + d.getSubject()
                        + ". Severity: " + d.getSeverity() + ".");
        sms(employee.getId(), "Pixous HR: a discipline record ("
                + d.getReferenceCode() + ") has been raised. Please check the portal.");
    }

    private void notifyCtoRaised(DisciplineRecord d, User employee, String reporter) {
        oversight.notifyCto(d.getReportedBy(),
                "Discipline record submitted " + d.getReferenceCode(),
                reporter + " raised a " + d.getSeverity().toLowerCase()
                        + "-severity record about " + employee.getName()
                        + (employee.getEmployeeCode() == null ? "" : " (" + employee.getEmployeeCode() + ")")
                        + ": " + d.getSubject(),
                "DISCIPLINE", "/discipline");
    }

    /** Never lets a notification failure lose the record that was saved. */
    private void notify(Long userId, String title, String body) {
        if (userId == null) return;
        try {
            notificationService.createAndPush(userId, title, body, "DISCIPLINE", "/discipline");
        } catch (Exception e) {
            log.warn("Discipline notification failed for {}: {}", userId, e.getMessage());
        }
    }

    private void sms(Long userId, String message) {
        if (userId == null) return;
        try {
            userRepository.findById(userId)
                    .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                    .ifPresent(u -> smsService.send(u.getPhone(), message));
        } catch (Exception e) {
            log.warn("Discipline SMS failed for {}: {}", userId, e.getMessage());
        }
    }

    private String nameOf(Long userId) {
        if (userId == null) return "Someone";
        return userRepository.findById(userId).map(User::getName).orElse("Someone");
    }

    private String generateCode() {
        // Counts up from the highest existing code rather than from the row
        // count: count()+1 regenerates a used code after any deletion, and the
        // column is unique, so the insert would fail.
        String prefix = "DSP-" + Year.now().getValue() + "-";
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

    private static String normalise(String value, Set<String> allowed, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String up = value.trim().toUpperCase().replace(' ', '_');
        return allowed.contains(up) ? up : fallback;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
