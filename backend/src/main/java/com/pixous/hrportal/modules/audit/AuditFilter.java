package com.pixous.hrportal.modules.audit;

import com.pixous.hrportal.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Records every request that changes something.
 *
 * <p>Written as a filter rather than as a call in each service on purpose: there
 * are dozens of endpoints and more arrive every week, and an audit trail that
 * depends on somebody remembering to add a line is an audit trail with holes in
 * it. Anything that is not a GET passes through here, so nothing can be
 * forgotten.
 *
 * <p>Services still record the actions that deserve a sentence and a
 * before-and-after — this is the floor, not the ceiling.
 *
 * <p>GETs are skipped. Recording every page somebody opened would write hundreds
 * of rows per person per day and bury the handful that matter; the reads worth
 * keeping are recorded by name where they happen (revealing a password,
 * downloading a payslip) rather than swept up indiscriminately.
 */
@Component
@Order(200)
@RequiredArgsConstructor
public class AuditFilter extends OncePerRequestFilter {

    private final AuditService audit;

    /** Paths that would only add noise: health checks, static files, polling. */
    private static final List<String> IGNORED_PREFIXES = List.of(
            "/api/files", "/actuator", "/api/auth/refresh", "/api/presence",
            "/api/calls/signal",           // one per ICE candidate — hundreds a call
            "/api/communities/messages",   // read receipts, fired per message seen
            // Sign-ins belong to login_history, which records the username, the
            // address, the device and whether it worked. They are deliberately not
            // recorded here as well: a login has no authenticated user yet, so this
            // filter can only produce a nameless "Signed in" row — worse than
            // nothing beside a history that knows exactly who it was.
            "/api/auth/login", "/api/auth/logout",
            "/ws", "/swagger", "/v3/api-docs"
    );

    /**
     * How a path is described in plain words, and which category it belongs to.
     *
     * <p>Ordered longest-prefix-first so the specific rule wins over the general
     * one — /api/payroll/payslip must not be described as merely payroll.
     */
    private static final List<Map.Entry<String, String[]>> RULES = List.of(
            Map.entry("/api/payroll/payslip/generate", new String[]{AuditService.PAYROLL, "Generated a payslip"}),
            Map.entry("/api/payroll/salary-months", new String[]{AuditService.PAYROLL, "Set month-wise basic pay"}),
            Map.entry("/api/payroll/salary", new String[]{AuditService.PAYROLL, "Changed a salary structure"}),
            Map.entry("/api/payroll/runs", new String[]{AuditService.PAYROLL, "Ran payroll"}),
            Map.entry("/api/payroll/requests", new String[]{AuditService.PAYROLL, "Handled a payslip request"}),
            Map.entry("/api/payroll", new String[]{AuditService.PAYROLL, "Payroll change"}),

            Map.entry("/api/users/documents", new String[]{AuditService.EMPLOYEE, "Uploaded an employee document"}),
            Map.entry("/api/users/face-photo", new String[]{AuditService.FACE, "Registered a face"}),
            Map.entry("/api/users/photo", new String[]{AuditService.EMPLOYEE, "Changed a profile photo"}),
            Map.entry("/api/users/password", new String[]{AuditService.SECURITY, "Changed a password"}),
            Map.entry("/api/users", new String[]{AuditService.EMPLOYEE, "Changed an employee record"}),

            Map.entry("/api/auth/employees/bulk", new String[]{AuditService.EMPLOYEE, "Imported employees from Excel"}),
            Map.entry("/api/auth/employees/imports", new String[]{AuditService.EMPLOYEE, "Undid an employee import"}),
            Map.entry("/api/auth/employees", new String[]{AuditService.EMPLOYEE, "Created an employee"}),
            Map.entry("/api/auth/login", new String[]{AuditService.SECURITY, "Signed in"}),
            Map.entry("/api/auth/logout", new String[]{AuditService.SECURITY, "Signed out"}),
            Map.entry("/api/auth", new String[]{AuditService.SECURITY, "Account action"}),

            Map.entry("/api/attendance/face-punch", new String[]{AuditService.ATTENDANCE, "Punched with face verification"}),
            Map.entry("/api/attendance/punch-in", new String[]{AuditService.ATTENDANCE, "Punched in"}),
            Map.entry("/api/attendance/punch-out", new String[]{AuditService.ATTENDANCE, "Punched out"}),
            Map.entry("/api/attendance", new String[]{AuditService.ATTENDANCE, "Attendance change"}),

            Map.entry("/api/leave/permissions", new String[]{AuditService.LEAVE, "Permission request"}),
            Map.entry("/api/leave/types", new String[]{AuditService.LEAVE, "Changed a leave policy"}),
            Map.entry("/api/leave", new String[]{AuditService.LEAVE, "Leave action"}),

            Map.entry("/api/communities/retention", new String[]{AuditService.CHAT, "Changed chat retention"}),
            Map.entry("/api/communities", new String[]{AuditService.CHAT, "Chat change"}),

            Map.entry("/api/admin/reset", new String[]{AuditService.SYSTEM, "Cleared records (Fresh Start)"}),
            Map.entry("/api/org", new String[]{AuditService.SYSTEM, "Changed organisation master data"}),
            Map.entry("/api/tasks", new String[]{AuditService.SYSTEM, "Task change"})
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod())
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return IGNORED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long started = System.currentTimeMillis();
        // The chain runs first: the status is part of what is being recorded, and a
        // refused action is as worth keeping as a successful one.
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                writeEntry(request, response, (int) (System.currentTimeMillis() - started));
            } catch (Exception ignored) {
                // Auditing must never be able to break a request. See AuditService.
            }
        }
    }

    private void writeEntry(HttpServletRequest request, HttpServletResponse response, int ms) {
        String path = request.getRequestURI();
        String[] rule = describe(path);
        int status = response.getStatus();

        // The signed-in user is read after the chain has run, so a login records
        // the account it just authenticated rather than nobody.
        Long actor = null;
        try {
            actor = SecurityUtils.currentUserId();
        } catch (Exception ignored) {
            // Not signed in — a failed login, for instance. Still worth recording.
        }

        String verb = switch (request.getMethod().toUpperCase()) {
            case "DELETE" -> "Deleted";
            case "PUT", "PATCH" -> "Updated";
            default -> null;
        };
        String summary = verb == null ? rule[1] : rule[1] + " (" + verb.toLowerCase() + ")";
        if (status >= 400) summary = summary + " — refused";

        audit.record(actor, rule[0], rule[1], summary,
                null, null, null, null,
                status < 400,
                request.getMethod(), path, status,
                clientIp(request), request.getHeader("User-Agent"), ms);
    }

    private static String[] describe(String path) {
        for (Map.Entry<String, String[]> r : RULES) {
            if (path.startsWith(r.getKey())) return r.getValue();
        }
        return new String[]{AuditService.SYSTEM, "Change"};
    }

    /** The caller's address, honouring a proxy header when nginx sets one. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
