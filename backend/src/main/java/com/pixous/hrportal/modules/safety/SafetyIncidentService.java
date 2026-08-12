package com.pixous.hrportal.modules.safety;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.safety.dto.SafetyIncidentRequest;
import com.pixous.hrportal.modules.safety.dto.SafetyIncidentResponse;
import com.pixous.hrportal.modules.safety.dto.SafetyResolutionRequest;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Set;

/** Safety Incidents: employees report; staff with REPORT_VIEW investigate and resolve. */
@Service
@RequiredArgsConstructor
public class SafetyIncidentService {

    private static final Set<String> VALID_INCIDENT_TYPE =
            Set.of("NEAR_MISS", "MINOR_INJURY", "MAJOR_INJURY", "PROPERTY_DAMAGE", "ENV_HAZARD");
    private static final Set<String> VALID_SEVERITY = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> VALID_STATUS =
            Set.of("OPEN", "INVESTIGATING", "RESOLVED", "CLOSED");

    private final SafetyIncidentRepository repository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.pixous.hrportal.common.SmsService smsService;

    /** Text a user if we have a usable mobile number for them. */
    private void sms(Long userId, String message) {
        if (userId == null) return;
        userRepository.findById(userId)
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + message));
    }

    @Transactional
    public SafetyIncidentResponse report(Long userId, SafetyIncidentRequest req) {
        SafetyIncident s = new SafetyIncident();
        s.setReferenceCode(generateCode());
        s.setReportedBy(userId);
        s.setIncidentType(normalise(req.incidentType(), VALID_INCIDENT_TYPE, "NEAR_MISS"));
        s.setDescription(req.description().trim());
        s.setZone(blankToNull(req.zone()));
        s.setAnonymous(req.anonymous());
        s.setSeverity(normalise(req.severity(), VALID_SEVERITY, "MEDIUM"));
        s.setStatus("OPEN");

        if (req.occurredAt() != null && !req.occurredAt().isBlank()) {
            s.setOccurredAt(LocalDateTime.parse(req.occurredAt()));
        }

        SafetyIncident saved = repository.save(s);

        // Notify all staff with REPORT_VIEW permission about the new incident.
        String submitter = req.anonymous() ? "Anonymous" : safeName(userId);
        userRepository.findByPermission("REPORT_VIEW").forEach(staff -> {
            if (!staff.getId().equals(userId)) {
                notificationService.createAndPush(staff.getId(),
                        "New safety incident: " + saved.getReferenceCode(),
                        submitter + " reported a " + saved.getIncidentType().toLowerCase().replace('_', ' '),
                        "SAFETY", "/safety-incidents");
                sms(staff.getId(), submitter + " reported a safety incident ("
                        + saved.getReferenceCode() + "). Please review in the portal.");
            }
        });

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SafetyIncidentResponse> myReports(Long userId, int page, int size) {
        Page<SafetyIncident> result =
                repository.findByReportedByOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<SafetyIncidentResponse> all(String status, String incidentType, int page, int size) {
        String statusFilter = (status == null || status.isBlank()) ? null : status.toUpperCase();
        String typeFilter = (incidentType == null || incidentType.isBlank()) ? null : incidentType.toUpperCase();
        Page<SafetyIncident> result =
                repository.filterAll(statusFilter, typeFilter, PageRequest.of(page, size));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SafetyIncidentResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public SafetyIncidentResponse resolve(Long staffId, Long id, SafetyResolutionRequest req) {
        SafetyIncident s = find(id);
        String status = req.status() == null ? "" : req.status().toUpperCase();
        if (!VALID_STATUS.contains(status)) {
            throw ApiException.business("Invalid status: " + req.status());
        }
        s.setStatus(status);
        if (req.resolutionNotes() != null && !req.resolutionNotes().isBlank()) {
            s.setResolutionNotes(req.resolutionNotes().trim());
        }
        s.setResolvedBy(staffId);
        if (status.equals("RESOLVED") || status.equals("CLOSED")) {
            s.setResolvedAt(LocalDateTime.now());
        } else {
            s.setResolvedAt(null);
        }
        SafetyIncident saved = repository.save(s);

        // Notify the original reporter of the update.
        if (saved.getReportedBy() != null) {
            notificationService.createAndPush(saved.getReportedBy(),
                    saved.getReferenceCode() + " updated",
                    "Your safety incident is now " + status.toLowerCase().replace('_', ' '),
                    "SAFETY", "/safety-incidents");
            sms(saved.getReportedBy(), saved.getReferenceCode() + " is now "
                    + status.toLowerCase().replace('_', ' ') + ".");
        }

        return toResponse(saved);
    }

    // ---- helpers ----

    private SafetyIncident find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Safety Incident"));
    }

    private SafetyIncidentResponse toResponse(SafetyIncident s) {
        String reportedByName = s.isAnonymous() ? "Anonymous" : safeName(s.getReportedBy());
        String resolvedByName = s.getResolvedBy() == null ? null : safeName(s.getResolvedBy());
        return SafetyIncidentResponse.from(s, reportedByName, resolvedByName);
    }

    private String safeName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getName).orElse("User");
    }

    private String generateCode() {
        long count = repository.count() + 1;
        return "SI-" + Year.now().getValue() + "-" + String.format("%05d", count);
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
