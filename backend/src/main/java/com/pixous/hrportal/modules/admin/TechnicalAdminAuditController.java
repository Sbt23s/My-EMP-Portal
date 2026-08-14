package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/technical-admin/audit-logs")
@PreAuthorize("hasRole('TECHNICAL_ADMIN')")
public class TechnicalAdminAuditController {

    private final TechnicalAuditLogRepository auditLogRepository;

    public TechnicalAdminAuditController(TechnicalAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllAuditLogs() {
        return ResponseEntity.ok(ApiResponse.ok(auditLogRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<?>> getCompanyAuditLogs(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.ok(auditLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)));
    }

    /**
     * Who used what, and for how long.
     *
     * <p>One row per person: which modules they touched, how many times, when
     * they started and when they were last seen. Aggregated on the server because
     * the raw rows are thousands and the answer is dozens — sending the former to
     * have the browser produce the latter wastes the trip.
     *
     * <p>Time is bounded, not measured: first touch to last touch on each day,
     * summed. Somebody who signs in at nine, works, and closes the tab at six
     * reads as nine hours; somebody who opens one page and leaves reads as
     * nothing. Measuring properly needs a heartbeat from every open tab, which is
     * a great deal of traffic for a number this well approximated — so the field
     * is named {@code activeMinutes} and means exactly what it says.
     *
     * @param companyId optional; omitted reports every company.
     * @param days how far back to look. Defaults to thirty.
     */
    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<?>> usage(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long companyId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int days) {

        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(Math.max(1, days));

        List<TechnicalAuditLog> rows = (companyId == null
                ? auditLogRepository.findAllByOrderByCreatedAtDesc()
                : auditLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .stream()
                .filter(r -> UsageTracker.ACTION.equals(r.getAction()))
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(since))
                .toList();

        // person -> their rows. LinkedHashMap so the response order is stable
        // between calls rather than shuffling on every refresh.
        Map<Long, List<TechnicalAuditLog>> byPerson = new LinkedHashMap<>();
        for (TechnicalAuditLog r : rows) {
            if (r.getAdminId() == null) continue;
            byPerson.computeIfAbsent(r.getAdminId(), k -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, List<TechnicalAuditLog>> entry : byPerson.entrySet()) {
            List<TechnicalAuditLog> mine = entry.getValue();

            Map<String, Long> perModule = new LinkedHashMap<>();
            // Per day, so an idle weekend between two visits is not counted as
            // time spent working.
            Map<java.time.LocalDate, long[]> spans = new LinkedHashMap<>();

            for (TechnicalAuditLog r : mine) {
                String module = r.getEntityType() == null ? "OTHER" : r.getEntityType();
                perModule.merge(module, 1L, Long::sum);

                java.time.LocalDate day = r.getCreatedAt().toLocalDate();
                long at = r.getCreatedAt().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                spans.compute(day, (d, span) -> span == null
                        ? new long[]{at, at}
                        : new long[]{Math.min(span[0], at), Math.max(span[1], at)});
            }

            long activeMinutes = spans.values().stream()
                    .mapToLong(span -> Math.max(0, (span[1] - span[0]) / 60_000L))
                    .sum();

            java.time.LocalDateTime first = mine.stream().map(TechnicalAuditLog::getCreatedAt)
                    .min(java.time.LocalDateTime::compareTo).orElse(null);
            java.time.LocalDateTime last = mine.stream().map(TechnicalAuditLog::getCreatedAt)
                    .max(java.time.LocalDateTime::compareTo).orElse(null);

            Map<String, Object> person = new LinkedHashMap<>();
            person.put("userId", entry.getKey());
            person.put("username", mine.get(0).getAdminUsername());
            person.put("companyId", mine.get(0).getCompanyId());
            person.put("touches", (long) mine.size());
            person.put("modules", perModule);
            person.put("daysActive", spans.size());
            person.put("activeMinutes", activeMinutes);
            person.put("firstSeen", first);
            person.put("lastSeen", last);
            out.add(person);
        }

        // Busiest first — the question is usually "who is actually using this".
        out.sort((a, b) -> Long.compare((Long) b.get("touches"), (Long) a.get("touches")));

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "days", days,
                "people", out,
                // Says plainly that nothing was recorded before the tracker
                // existed, so an empty table is not read as "nobody works here".
                "note", "Usage has been recorded since this feature was deployed; "
                        + "earlier activity was never stored.")));
    }
}
