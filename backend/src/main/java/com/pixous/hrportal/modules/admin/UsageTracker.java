package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who used which part of the portal, and when.
 *
 * <p>The technical-admin audit page could only report what technical admins did.
 * "Which of this company's people actually use the leave module, and for how
 * long" had no answer anywhere, because nothing recorded ordinary use.
 *
 * <p>Three deliberate constraints, all of them about not becoming the problem:
 *
 * <ul>
 *   <li><b>No new table.</b> Rows go into {@code technical_audit_logs}, which
 *       already carries a company, a person, an action and a timestamp. The
 *       hosting account allows twenty database connections in total; a migration
 *       on a live database with sixty-two people working in it is a risk worth
 *       taking only when there is no alternative, and here there is one.
 *   <li><b>Throttled hard.</b> At most one row per person per module per ten
 *       minutes, decided in memory before any database work. Without it a single
 *       dashboard — which fires a dozen requests — would write a dozen rows, and
 *       a busy morning would spend the connection budget on bookkeeping.
 *   <li><b>Never fails the request.</b> Recording that somebody opened the leave
 *       page must not be able to stop them opening it.
 * </ul>
 *
 * <p>Duration is not measured, it is inferred: the first and last touch of a day
 * bound the time somebody was working, and the touch count says how busy it was.
 * Measuring properly would mean a heartbeat from every open tab, which is a lot
 * of traffic to answer a question this well approximated.
 */
@Component
public class UsageTracker implements HandlerInterceptor {

    /** Marks a row as ordinary use rather than an administrative change. */
    public static final String ACTION = "MODULE_USE";

    private static final long THROTTLE_MS = 10 * 60 * 1000L;

    /**
     * Guards against unbounded growth on a long-running server.
     *
     * One entry per person per module; a large tenant with every module on is a
     * few thousand. Past the cap the map is cleared rather than pruned — losing
     * the throttle for one ten-minute window costs a few extra rows, and the
     * alternative is tracking access order for something this disposable.
     */
    private static final int MAX_TRACKED = 20_000;

    private final Map<String, Long> lastWrite = new ConcurrentHashMap<>();
    private final TechnicalAuditLogRepository repository;

    public UsageTracker(TechnicalAuditLogRepository repository) {
        this.repository = repository;
    }

    /** Request path to module code. Anything unmapped is not recorded. */
    private static String moduleFor(String path) {
        if (path == null) return null;
        // Longest prefixes first: /leave/policies must not answer as /leave.
        if (path.startsWith("/api/attendance")) return "ATTENDANCE";
        if (path.startsWith("/api/leave") || path.startsWith("/api/permissions")) return "LEAVE";
        if (path.startsWith("/api/payroll") || path.startsWith("/api/payslips")) return "PAYROLL";
        if (path.startsWith("/api/tasks")) return "TASKS";
        if (path.startsWith("/api/work-reports") || path.startsWith("/api/reports")) return "REPORTS";
        if (path.startsWith("/api/ta-expenses") || path.startsWith("/api/expenses")) return "EXPENSES";
        if (path.startsWith("/api/assets")) return "ASSETS";
        if (path.startsWith("/api/helpdesk") || path.startsWith("/api/complaints")) return "HELPDESK";
        if (path.startsWith("/api/chat") || path.startsWith("/api/calls")) return "CHAT";
        if (path.startsWith("/api/calendar") || path.startsWith("/api/holidays")) return "CALENDAR";
        if (path.startsWith("/api/teams")) return "TEAMS";
        if (path.startsWith("/api/documents")) return "DOCUMENTS";
        if (path.startsWith("/api/projects")) return "PROJECTS";
        if (path.startsWith("/api/communities")) return "COMMUNITIES";
        if (path.startsWith("/api/dashboard")) return "DASHBOARD";
        return null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            record(request);
        } catch (Exception ignored) {
            // Bookkeeping must never be the reason a page fails to open.
        }
        return true;
    }

    private void record(HttpServletRequest request) {
        // Reads only. Opening a page is use; so is saving, and both are already
        // covered — but a GET storm is what the throttle is for.
        String module = moduleFor(request.getRequestURI());
        if (module == null) return;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) return;
        // A technical admin has no company of their own, and their actions are
        // recorded separately by TechnicalAuditService.
        if (principal.getCompanyId() == null) return;

        String key = principal.getId() + "|" + module;
        long now = System.currentTimeMillis();
        Long previous = lastWrite.get(key);
        if (previous != null && now - previous < THROTTLE_MS) return;

        if (lastWrite.size() > MAX_TRACKED) lastWrite.clear();
        lastWrite.put(key, now);

        write(principal.getCompanyId(), principal.getId(), principal.getUsername(), module,
                clientIp(request));
    }

    /**
     * Its own transaction, so a row is kept even if the request that produced it
     * goes on to roll back — and so a failure here cannot mark the caller's
     * transaction for rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void write(Long companyId, Long userId, String username, String module, String ip) {
        try {
            TechnicalAuditLog row = new TechnicalAuditLog();
            row.setCompanyId(companyId);
            row.setAdminId(userId);
            row.setAdminUsername(username);
            row.setAction(ACTION);
            row.setEntityType(module);
            row.setIpAddress(ip);
            repository.save(row);
        } catch (Exception ignored) {
            // Same reason as above.
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // The client is the first entry; the rest are proxies.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
