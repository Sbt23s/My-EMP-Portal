package com.pixous.hrportal.modules.audit;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.auth.LoginHistory;
import com.pixous.hrportal.modules.auth.LoginHistoryRepository;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * The audit trail, for HR and the admin.
 *
 * <p>Two things are read here and they answer different questions. The trail
 * itself says what was done — a salary changed, a payslip issued, an employee
 * removed. The login history says who came in, from where, on what, and whether
 * they got in. Together they are the two halves of "who did this".
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Who did what, when, and from where")
@RequiredArgsConstructor
// Reading an audit trail is itself a privileged act: it names who did what and
// from which address. HR and the admin only.
@PreAuthorize("hasAnyAuthority('USER_MANAGE', 'EMPLOYEE_MANAGE')")
public class AuditController {

    private final AuditEntryRepository repository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserRepository userRepository;

    /** The trail, newest first, filtered the ways somebody actually asks. */
    @GetMapping
    @Operation(summary = "HR/Admin: the audit trail")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean onlyFailures,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDateTime start = parseFrom(from);
        LocalDateTime end = parseTo(to);
        String needle = (q == null || q.isBlank()) ? null : q.trim();
        String cat = (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category))
                ? null : category.trim().toUpperCase();

        Page<AuditEntry> found = repository.search(start, end, userId, cat, onlyFailures, needle,
                PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", found.getContent().stream().map(AuditController::toRow).toList());
        out.put("totalElements", found.getTotalElements());
        out.put("totalPages", found.getTotalPages());
        out.put("page", found.getNumber());
        out.put("size", found.getSize());
        return ApiResponse.ok(out);
    }

    /**
     * The counts above the table: how much of each kind, who is busiest, and how
     * many actions were refused — a run of refusals is the thing worth spotting.
     */
    @GetMapping("/summary")
    @Operation(summary = "HR/Admin: audit counts for a period")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = parseFrom(from);
        LocalDateTime end = parseTo(to);

        List<Map<String, Object>> categories = new ArrayList<>();
        long total = 0;
        for (Object[] r : repository.countByCategory(start, end)) {
            long n = ((Number) r[1]).longValue();
            total += n;
            categories.add(Map.of("category", r[0], "count", n));
        }

        List<Map<String, Object>> people = new ArrayList<>();
        for (Object[] r : repository.countByUser(start, end)) {
            if (people.size() >= 8) break;
            people.add(new LinkedHashMap<>(Map.of(
                    "userId", r[0] == null ? 0 : r[0],
                    "name", r[1] == null ? "—" : r[1],
                    "employeeCode", r[2] == null ? "" : r[2],
                    "count", ((Number) r[3]).longValue())));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("refused", repository.countByAtBetweenAndSucceededFalse(start, end));
        out.put("categories", categories);
        out.put("busiest", people);
        out.put("from", start.toString());
        out.put("to", end.toString());
        return ApiResponse.ok(out);
    }

    /**
     * Sign-ins: who, when, from which address, on what device, and whether they
     * got in. A failed attempt is kept — a run of them is the point.
     */
    @GetMapping("/logins")
    @Operation(summary = "HR/Admin: login history")
    public ApiResponse<Map<String, Object>> logins(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean onlyFailures,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDateTime start = parseFrom(from);
        LocalDateTime end = parseTo(to);
        String needle = q == null ? "" : q.trim().toLowerCase();

        // Names come from the accounts, so a history row can be searched by the
        // person's name and not only by the username they typed.
        Map<Long, User> byId = new HashMap<>();
        userRepository.findAll().forEach(u -> byId.put(u.getId(), u));

        List<Map<String, Object>> all = loginHistoryRepository.findAll().stream()
                .filter(h -> h.getCreatedAt() != null
                        && !h.getCreatedAt().isBefore(start) && !h.getCreatedAt().isAfter(end))
                .filter(h -> !onlyFailures || !h.isSuccess())
                .sorted(Comparator.comparing(LoginHistory::getCreatedAt).reversed())
                .map(h -> {
                    User u = h.getUserId() == null ? null : byId.get(h.getUserId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", h.getId());
                    m.put("at", h.getCreatedAt());
                    m.put("userId", h.getUserId());
                    m.put("name", u == null ? null : u.getName());
                    m.put("employeeCode", u == null ? null : u.getEmployeeCode());
                    m.put("username", h.getUsername());
                    m.put("success", h.isSuccess());
                    m.put("ipAddress", h.getIpAddress());
                    m.put("device", h.getUserAgent());
                    m.put("client", describeClient(h.getUserAgent()));
                    return m;
                })
                .filter(m -> needle.isEmpty()
                        || String.valueOf(m.get("name")).toLowerCase().contains(needle)
                        || String.valueOf(m.get("username")).toLowerCase().contains(needle)
                        || String.valueOf(m.get("employeeCode")).toLowerCase().contains(needle)
                        || String.valueOf(m.get("ipAddress")).toLowerCase().contains(needle))
                .toList();

        int s = Math.min(200, Math.max(1, size));
        int p = Math.max(0, page);
        int fromIdx = Math.min(p * s, all.size());
        int toIdx = Math.min(fromIdx + s, all.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", all.subList(fromIdx, toIdx));
        out.put("totalElements", all.size());
        out.put("totalPages", (int) Math.ceil(all.size() / (double) s));
        out.put("page", p);
        out.put("size", s);
        out.put("failed", all.stream().filter(m -> Boolean.FALSE.equals(m.get("success"))).count());
        return ApiResponse.ok(out);
    }

    /** Everything ever done to one record, for the history on that record. */
    @GetMapping("/entity")
    @Operation(summary = "HR/Admin: the trail for one record")
    public ApiResponse<List<Map<String, Object>>> forEntity(
            @RequestParam String type, @RequestParam String id) {
        return ApiResponse.ok(
                repository.findByEntityTypeAndEntityIdOrderByAtDesc(type, id).stream()
                        .map(AuditController::toRow).toList());
    }

    // ---- helpers ----

    private static Map<String, Object> toRow(AuditEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("at", e.getAt());
        m.put("userId", e.getUserId());
        m.put("name", e.getUserName());
        m.put("employeeCode", e.getEmployeeCode());
        m.put("roles", e.getRoles());
        m.put("category", e.getCategory());
        m.put("action", e.getAction());
        m.put("summary", e.getSummary());
        m.put("entityType", e.getEntityType());
        m.put("entityId", e.getEntityId());
        m.put("entityLabel", e.getEntityLabel());
        m.put("detail", e.getDetail());
        m.put("method", e.getMethod());
        m.put("path", e.getPath());
        m.put("status", e.getStatus());
        m.put("ipAddress", e.getIpAddress());
        m.put("device", e.getDevice());
        m.put("client", describeClient(e.getDevice()));
        m.put("durationMs", e.getDurationMs());
        m.put("succeeded", e.isSucceeded());
        return m;
    }

    /**
     * A user-agent string in words. Nobody reads a raw one, and the question being
     * asked is only ever "was that the app, a phone, or a desktop browser".
     */
    static String describeClient(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase();
        String app = ua.contains("okhttp") || ua.contains("dart") || ua.contains("reactnative")
                || ua.contains("pixous") ? "Mobile app"
                : ua.contains("edg/") ? "Edge"
                : ua.contains("chrome") && !ua.contains("chromium") ? "Chrome"
                : ua.contains("firefox") ? "Firefox"
                : ua.contains("safari") ? "Safari"
                : "Other";
        String os = ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("windows") ? "Windows"
                : ua.contains("mac os") ? "macOS"
                : ua.contains("linux") ? "Linux"
                : "";
        return os.isEmpty() ? app : app + " on " + os;
    }

    private static LocalDateTime parseFrom(String s) {
        try {
            if (s != null && !s.isBlank()) return LocalDate.parse(s).atStartOfDay();
        } catch (Exception ignored) { }
        return LocalDate.now().minusDays(30).atStartOfDay();
    }

    private static LocalDateTime parseTo(String s) {
        try {
            if (s != null && !s.isBlank()) return LocalDate.parse(s).atTime(LocalTime.MAX);
        } catch (Exception ignored) { }
        return LocalDate.now().atTime(LocalTime.MAX);
    }
}
