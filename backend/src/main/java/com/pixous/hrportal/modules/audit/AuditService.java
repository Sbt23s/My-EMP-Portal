package com.pixous.hrportal.modules.audit;

import com.pixous.hrportal.modules.user.Role;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Writing the audit trail.
 *
 * <p>Every write here runs in its own transaction. An audit entry is a record of
 * something that happened, so it must survive the thing it describes rolling back
 * — and, more importantly, failing to write one must never be able to fail the
 * action it was describing. Recording that a salary changed is valuable; refusing
 * the salary change because the recording failed is not.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEntryRepository repository;
    private final UserRepository userRepository;

    /** Categories, so the page can group them and the eye can skim. */
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String PAYROLL = "PAYROLL";
    public static final String ATTENDANCE = "ATTENDANCE";
    public static final String LEAVE = "LEAVE";
    public static final String FACE = "FACE";
    public static final String CHAT = "CHAT";
    public static final String SECURITY = "SECURITY";
    public static final String SYSTEM = "SYSTEM";

    /**
     * Records one action. Never throws: an audit failure is logged and swallowed,
     * because the alternative is an audit table that can break the portal.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String category, String action, String summary,
                       String entityType, Object entityId, String entityLabel,
                       String detail, boolean succeeded,
                       String method, String path, Integer status,
                       String ip, String device, Integer durationMs) {
        try {
            AuditEntry e = new AuditEntry();
            e.setAt(LocalDateTime.now());
            e.setCategory(category == null ? SYSTEM : category);
            e.setAction(action);
            e.setSummary(clip(summary, 500));
            e.setEntityType(entityType);
            e.setEntityId(entityId == null ? null : clip(String.valueOf(entityId), 60));
            e.setEntityLabel(clip(entityLabel, 200));
            e.setDetail(clip(detail, 60_000));
            e.setSucceeded(succeeded);
            e.setMethod(method);
            e.setPath(clip(path, 400));
            e.setStatus(status);
            e.setIpAddress(clip(ip, 45));
            e.setDevice(clip(device, 255));
            e.setDurationMs(durationMs);

            if (actorId != null) {
                e.setUserId(actorId);
                userRepository.findById(actorId).ifPresent(u -> {
                    e.setUserName(u.getName());
                    e.setEmployeeCode(u.getEmployeeCode());
                    e.setRoles(clip(u.getRoles().stream().map(Role::getCode)
                            .collect(Collectors.joining(",")), 255));
                });
            }
            repository.save(e);
        } catch (Exception ex) {
            // Deliberately swallowed. See the note on this class.
            log.warn("Could not write an audit entry for {} {}", category, action, ex);
        }
    }

    /** The short form, for a service that knows what it did and about whom. */
    public void record(Long actorId, String category, String action, String summary,
                       String entityType, Object entityId, String entityLabel) {
        record(actorId, category, action, summary, entityType, entityId, entityLabel,
                null, true, null, null, null, null, null, null);
    }

    /** With a before-and-after worth keeping. */
    public void recordChange(Long actorId, String category, String action, String summary,
                             String entityType, Object entityId, String entityLabel,
                             String before, String after) {
        String detail = null;
        if (before != null || after != null) {
            detail = "{\"before\":" + json(before) + ",\"after\":" + json(after) + "}";
        }
        record(actorId, category, action, summary, entityType, entityId, entityLabel,
                detail, true, null, null, null, null, null, null);
    }

    private static String json(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ") + "\"";
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
