package com.pixous.hrportal.modules.admin;

import com.pixous.hrportal.security.TechnicalAdminPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Records what a technical admin did.
 *
 * <p>The audit page, its controller, the entity and the repository were all
 * built and correct; nothing ever wrote a row, so the page showed an empty
 * table under a heading promising real-time monitoring. This is the missing
 * half.
 *
 * <p>Scope is deliberately narrow: administrative actions — a module switched,
 * a company edited, an account created or removed. Not page views. Recording
 * every request would mean a write per request against a database that allows
 * twenty connections in total, and the tracking would become the outage.
 */
@Service
public class TechnicalAuditService {

    private static final Logger log = LoggerFactory.getLogger(TechnicalAuditService.class);

    /** Long enough for a company name or a module code; a JSON blob is not wanted here. */
    private static final int MAX_VALUE = 500;

    private final TechnicalAuditLogRepository repository;

    public TechnicalAuditService(TechnicalAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Write one audit row.
     *
     * <p>In its own transaction, and never allowed to fail the caller. An audit
     * row is a record of something that already happened — if writing it goes
     * wrong, the module has still been switched, and rolling that back because
     * the note about it failed would be the wrong way round.
     *
     * @param companyId  which tenant the action was against, null if none
     * @param action     what happened, e.g. MODULE_ENABLED
     * @param entityType what it was done to, e.g. CompanyModule
     * @param entityId   which one, null when not applicable
     * @param oldValue   what it was, null when creating
     * @param newValue   what it became, null when deleting
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long companyId, String action, String entityType,
                       Long entityId, String oldValue, String newValue) {
        try {
            TechnicalAuditLog row = new TechnicalAuditLog();
            row.setCompanyId(companyId);
            row.setAction(action);
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setOldValue(trim(oldValue));
            row.setNewValue(trim(newValue));

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof TechnicalAdminPrincipal admin) {
                row.setAdminId(admin.getId());
                row.setAdminUsername(admin.getUsername());
            } else if (auth != null) {
                // Someone other than a technical admin reached an audited action.
                // Worth recording by name rather than dropping.
                row.setAdminUsername(auth.getName());
            }

            row.setIpAddress(callerIp());
            repository.save(row);
        } catch (Exception e) {
            log.warn("Could not write audit row for {} on {}", action, entityType, e);
        }
    }

    private String trim(String value) {
        if (value == null) return null;
        return value.length() <= MAX_VALUE ? value : value.substring(0, MAX_VALUE);
    }

    /**
     * The caller's address, reading the proxy header first.
     *
     * <p>Behind nginx every request appears to come from the container's own
     * gateway, so without this every row would record the same useless address.
     * X-Forwarded-For is a list when there are several proxies; the first entry
     * is the original client.
     */
    private String callerIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes servlet)) return null;
            HttpServletRequest req = servlet.getRequest();

            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
