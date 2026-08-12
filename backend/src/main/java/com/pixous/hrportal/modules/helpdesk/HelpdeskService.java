package com.pixous.hrportal.modules.helpdesk;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.common.PageResponse;
import com.pixous.hrportal.modules.helpdesk.dto.*;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HelpdeskService {

    private static final Set<String> VALID_STATUS = Set.of(
            "OPEN", "IN_PROGRESS", "AWAITING_PARTS", "RESOLVED", "CLOSED");

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
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

    /** Employee code of the one person HR's own support requests go to. */
    private static final String HR_TICKET_APPROVER_CODE = "PIX-E100";

    private static boolean isHrRole(User u) {
        return u != null && u.getRoles().stream()
                .map(com.pixous.hrportal.modules.user.Role::getCode)
                .anyMatch(c -> "IT_MGR".equals(c) || "IT_HR".equals(c) || "CV_HR".equals(c));
    }

    /**
     * Who a support request may be addressed to, which depends on who is asking:
     *  - an employee or a Team Leader -> HR, and only HR
     *  - HR -> the one person above them, matched on employee code so no other
     *    admin account can stand in
     * Nobody ever appears in their own list: a request you would handle yourself
     * is not a request.
     */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> agents(Long requesterId) {
        User me = requesterId == null ? null : userRepository.findById(requesterId).orElse(null);
        boolean iAmHr = isHrRole(me);

        java.util.stream.Stream<User> candidates = iAmHr
                // Looked up by code, not by permission: whether he holds the
                // helpdesk permission is not the question, he is the person.
                ? userRepository.findByEmployeeCode(HR_TICKET_APPROVER_CODE).stream()
                : userRepository.findByPermission("HELPDESK_AGENT").stream().filter(HelpdeskService::isHrRole);

        return candidates
                .filter(User::isEnabled)
                .filter(u -> !u.getId().equals(requesterId))
                .map(u -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getName());
                    m.put("code", u.getEmployeeCode());
                    m.put("designation", u.getDesignationTitle());
                    return m;
                }).toList();
    }

    @Transactional
    public TicketResponse raise(Long userId, TicketRequest req) {
        User raiser = userRepository.findById(userId).orElse(null);
        Ticket t = new Ticket();
        t.setTicketCode(generateTicketCode());
        t.setRaisedBy(userId);
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setAttachments(req.attachments() == null || req.attachments().isBlank()
                ? null : req.attachments().trim());
        t.setType(req.type() == null ? "IT" : req.type().toUpperCase());
        // Category follows the raiser's business division, not a manual pick:
        // Civil/Infra staff -> "Infra", IT/Digital staff -> "Digital".
        t.setCategory(divisionCategory(raiser));
        t.setPriority(req.priority() == null ? "MEDIUM" : req.priority().toUpperCase());
        t.setStatus("OPEN");
        t.setSlaDueAt(slaDue(t.getPriority()));
        if (req.assignedTo() != null) t.setAssignedTo(req.assignedTo());
        Ticket saved = ticketRepository.save(t);
        // Notify the HR this request is addressed to.
        if (saved.getAssignedTo() != null && !saved.getAssignedTo().equals(userId)) {
            notificationService.createAndPush(saved.getAssignedTo(),
                    "New support request " + saved.getTicketCode(),
                    safeName(userId) + " raised a support request",
                    "HELPDESK", "/helpdesk");
            sms(saved.getAssignedTo(), safeName(userId) + " raised support request "
                    + saved.getTicketCode() + ": " + saved.getTitle());
        }
        return toResponse(saved);
    }

    /**
     * The person who raised a ticket corrects it. Only while it is still OPEN --
     * once an agent has picked it up, the details they are working from should
     * not shift underneath them; a reply on the thread is the way to add to it.
     *
     * The code, the category and the status are not the raiser's to change.
     */
    @Transactional
    public TicketResponse updateOwn(Long userId, Long ticketId, TicketRequest req) {
        Ticket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Ticket"));
        if (!userId.equals(t.getRaisedBy())) {
            throw ApiException.business("You can only edit tickets you raised");
        }
        if (!"OPEN".equalsIgnoreCase(t.getStatus())) {
            throw ApiException.business(
                    "This ticket is already " + t.getStatus().toLowerCase().replace('_', ' ')
                            + " — add a reply instead of editing it");
        }

        t.setTitle(req.title());
        t.setDescription(req.description());
        if (req.attachments() != null) {
            t.setAttachments(req.attachments().isBlank() ? null : req.attachments().trim());
        }
        if (req.type() != null && !req.type().isBlank()) t.setType(req.type().toUpperCase());
        if (req.priority() != null && !req.priority().isBlank()) {
            t.setPriority(req.priority().toUpperCase());
            t.setSlaDueAt(slaDue(t.getPriority()));
        }
        Long previous = t.getAssignedTo();
        if (req.assignedTo() != null) t.setAssignedTo(req.assignedTo());
        t.setUpdatedAt(LocalDateTime.now());
        Ticket saved = ticketRepository.save(t);

        // Whoever it is addressed to is working from these details, so tell them.
        if (saved.getAssignedTo() != null && !saved.getAssignedTo().equals(userId)) {
            notificationService.createAndPush(saved.getAssignedTo(),
                    "Support request " + saved.getTicketCode() + " updated",
                    safeName(userId) + " updated their support request",
                    "HELPDESK", "/helpdesk");
        }
        // If it was handed to someone else, the previous recipient should know.
        if (previous != null && !previous.equals(saved.getAssignedTo())) {
            notificationService.createAndPush(previous,
                    "Support request " + saved.getTicketCode() + " reassigned",
                    safeName(userId) + " sent this request to someone else",
                    "HELPDESK", "/helpdesk");
        }
        return toResponse(saved);
    }

    /** The raiser's division determines the ticket category. */
    private String divisionCategory(User raiser) {
        return raiser != null && "CIVIL".equalsIgnoreCase(raiser.getIndustry()) ? "Infra" : "Digital";
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> myTickets(Long userId, int page, int size) {
        Page<Ticket> result = ticketRepository
                .findByRaisedByOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(result.map(this::toResponseNoComments));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> agentQueue(Long agentId, String status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        Page<Ticket> result = (status != null && !status.isBlank())
                ? ticketRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable)
                : ticketRepository.findByAssignedToOrderByCreatedAtDesc(agentId, pageable);
        return PageResponse.from(result.map(this::toResponseNoComments));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> allTickets(String status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        Page<Ticket> result = (status != null && !status.isBlank())
                ? ticketRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable)
                : ticketRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.from(result.map(this::toResponseNoComments));
    }

    @Transactional(readOnly = true)
    public TicketResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public CommentResponse addComment(Long userId, Long ticketId, CommentRequest req) {
        Ticket t = find(ticketId);
        TicketComment c = new TicketComment();
        c.setTicketId(ticketId);
        c.setAuthorId(userId);
        c.setComment(req.comment());
        c.setAttachmentPath(req.attachmentPath());
        TicketComment saved = commentRepository.save(c);

        // Notify the other party
        Long notifyTarget = userId.equals(t.getRaisedBy()) ? t.getAssignedTo() : t.getRaisedBy();
        if (notifyTarget != null && !notifyTarget.equals(userId)) {
            notificationService.createAndPush(notifyTarget,
                    "New comment on " + t.getTicketCode(),
                    safeName(userId) + " commented on your ticket",
                    "HELPDESK", "/helpdesk");
            sms(notifyTarget, safeName(userId) + " commented on ticket " + t.getTicketCode() + ".");
        }
        return CommentResponse.from(saved, safeName(userId));
    }

    @Transactional
    public TicketResponse changeStatus(Long actorId, Long ticketId, StatusRequest req) {
        Ticket t = find(ticketId);
        if (actorId != null && actorId.equals(t.getRaisedBy())) {
            throw ApiException.business(
                    "You raised this request, so it is not yours to decide — "
                            + "the person it was sent to will handle it");
        }
        // Addressed to someone: only they may move it along. A ticket from
        // before recipients were recorded is open to any agent.
        if (t.getAssignedTo() != null && actorId != null && !actorId.equals(t.getAssignedTo())) {
            throw ApiException.business("This request was sent to someone else to handle");
        }
        String status = req.status() == null ? "" : req.status().toUpperCase();
        if (!VALID_STATUS.contains(status)) {
            throw ApiException.business("Invalid status: " + req.status());
        }

        List<String> statuses = List.of("OPEN", "IN_PROGRESS", "AWAITING_PARTS", "RESOLVED", "CLOSED");
        int currentIdx = statuses.indexOf(t.getStatus());
        int targetIdx = statuses.indexOf(status);
        if (currentIdx != -1 && targetIdx != -1) {
            boolean allowed = false;
            if (targetIdx > currentIdx) {
                if (currentIdx == 1) {
                    allowed = (targetIdx == 2 || targetIdx == 3);
                } else {
                    allowed = (targetIdx == currentIdx + 1);
                }
            }
            if (!allowed) {
                throw ApiException.business("Invalid status transition from " + t.getStatus() + " to " + status);
            }
        }

        t.setStatus(status);
        if (req.assignTo() != null) {
            t.setAssignedTo(req.assignTo());
        }
        if (status.equals("RESOLVED") || status.equals("CLOSED")) {
            t.setResolvedAt(LocalDateTime.now());
        }
        t.setUpdatedAt(LocalDateTime.now());

        notificationService.createAndPush(t.getRaisedBy(),
                "Ticket " + t.getTicketCode() + " " + status.toLowerCase().replace('_', ' '),
                "Your ticket status changed to " + status,
                "HELPDESK", "/helpdesk");
        sms(t.getRaisedBy(), "Your support ticket " + t.getTicketCode() + " is now " + status + ".");
        return toResponse(t);
    }

    @Transactional
    public TicketResponse rate(Long userId, Long ticketId, RatingRequest req) {
        Ticket t = find(ticketId);
        if (!t.getRaisedBy().equals(userId)) {
            throw ApiException.business("Only the requester can rate this ticket");
        }
        if (!"RESOLVED".equals(t.getStatus()) && !"CLOSED".equals(t.getStatus())) {
            throw ApiException.business("Only resolved tickets can be rated");
        }
        t.setRating(req.rating());
        t.setUpdatedAt(LocalDateTime.now());
        return toResponse(t);
    }

    // ---- helpers ----

    private Ticket find(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> ApiException.notFound("Ticket"));
    }

    private LocalDateTime slaDue(String priority) {
        LocalDateTime now = LocalDateTime.now();
        return switch (priority) {
            case "CRITICAL" -> now.plusHours(4);
            case "HIGH" -> now.plusHours(8);
            case "MEDIUM" -> now.plusHours(24);
            default -> now.plusHours(48);
        };
    }

    private String generateTicketCode() {
        long count = ticketRepository.count() + 1;
        return "TKT-" + Year.now().getValue() + "-" + String.format("%05d", count);
    }

    private String safeName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse("User");
    }

    private TicketResponse toResponse(Ticket t) {
        List<CommentResponse> comments = commentRepository
                .findByTicketIdOrderByCreatedAtAsc(t.getId()).stream()
                .map(c -> CommentResponse.from(c, safeName(c.getAuthorId())))
                .toList();
        return build(t, comments);
    }

    private TicketResponse toResponseNoComments(Ticket t) {
        return build(t, List.of());
    }

    private TicketResponse build(Ticket t, List<CommentResponse> comments) {
        User raiser = userRepository.findById(t.getRaisedBy()).orElse(null);
        String raisedByName = raiser != null ? raiser.getName() : "User";
        String raisedByCode = raiser != null ? raiser.getEmployeeCode() : null;
        String assignedToName = t.getAssignedTo() == null ? null : safeName(t.getAssignedTo());
        return TicketResponse.from(t, raisedByName, raisedByCode, assignedToName, comments);
    }
}
