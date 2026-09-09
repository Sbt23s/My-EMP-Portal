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
    /**
     * The administrator's configuration of who this module may address.
     *
     * Narrows the list built below; a module with nothing configured is
     * unrestricted, so this changes nothing until somebody sets it.
     */
    private final com.pixous.hrportal.modules.approvalconfig.ApprovalRecipientService approvalRecipients;
    private final com.pixous.hrportal.modules.notification.OversightNotifier oversight;

    /** Text a user if we have a usable mobile number for them. */
    private void sms(Long userId, String message) {
        if (userId == null) return;
        userRepository.findById(userId)
                .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
                .ifPresent(u -> smsService.send(u.getPhone(), "Pixous HR: " + message));
    }

    /** Employee code of the one person HR's own support requests go to. */
    private static final String HR_TICKET_APPROVER_CODE = "PIX-E100";

    /**
     * The HR desk.
     *
     * <p>Three codes rather than one because the desk really is three: IT_HR
     * and CV_HR are the HR desks on either side of the business, and the
     * account everybody calls "HR" holds IT_MGR. Named here so the role test
     * and the notification fan-out cannot drift apart.
     */
    static final java.util.List<String> HR_DESK = java.util.List.of("IT_HR", "CV_HR", "IT_MGR");

    private static boolean isHrRole(User u) {
        return u != null && u.getRoles().stream()
                .map(com.pixous.hrportal.modules.user.Role::getCode)
                .anyMatch(HR_DESK::contains);
    }

    /**
     * Who a support request may be addressed to, which depends on who is asking:
     *  - an employee or a Team Leader -> HR, and only HR
     *  - HR -> the one person above them, matched on employee code so no other
     *    admin account can stand in
     * Nobody ever appears in their own list: a request you would handle yourself
     * is not a request.
     */
    private static boolean isSystemAdminRole(User u) {
        if (u == null) return false;
        boolean hasRole = u.getRoles().stream()
                .map(com.pixous.hrportal.modules.user.Role::getCode)
                .anyMatch(c -> "SUPER_ADMIN".equals(c) || "COMPANY_ADMIN".equals(c));
        boolean hasDesig = u.getDesignationTitle() != null && u.getDesignationTitle().toLowerCase().contains("admin");
        return hasRole || hasDesig || "PIX-E001".equals(u.getEmployeeCode());
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> agents(Long requesterId) {
        User me = requesterId == null ? null : userRepository.findById(requesterId).orElse(null);
        boolean iAmHr = isHrRole(me);

        java.util.Map<Long, java.util.Map<String, Object>> map = new java.util.LinkedHashMap<>();

        // 1. Always include CTO Elamaran Subramanian (PIX-E100)
        userRepository.findByEmployeeCode(HR_TICKET_APPROVER_CODE).ifPresent(u -> {
            if (u.isEnabled() && !u.getId().equals(requesterId)) {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", u.getId());
                m.put("name", "CTO (" + u.getEmployeeCode() + ")");
                m.put("code", u.getEmployeeCode());
                m.put("designation", "CTO");
                map.put(u.getId(), m);
            }
        });

        // 2. Always include single System Admin
        userRepository.findByPermission("USER_MANAGE").stream()
                .filter(User::isEnabled)
                .filter(u -> !u.getId().equals(requesterId))
                .filter(u -> !HR_TICKET_APPROVER_CODE.equalsIgnoreCase(u.getEmployeeCode()))
                .findFirst()
                .ifPresent(u -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("name", "System Admin (" + u.getEmployeeCode() + ")");
                    m.put("code", u.getEmployeeCode());
                    m.put("designation", "System Admin");
                    map.putIfAbsent(u.getId(), m);
                });

        /*
         * 3. For employees and Team Leaders: every member of the HR desk, by
         *    name.
         *
         * <p>This used to be findFirst -- one entry reading "HR (CODE)". It
         * made the desk look like a single mailbox, so every request in the
         * company went to whichever account the query happened to return first,
         * and the rest of HR had nothing addressed to them at all. Since the
         * addressee is who decides a request, that one person also did all the
         * deciding.
         *
         * <p>Names rather than the word "HR", because somebody choosing where
         * to send a problem is choosing a person: they know who they spoke to
         * last week, and "HR" tells them nothing about who will read it.
         *
         * <p>Sorted by name so the list does not reorder itself between page
         * loads -- a picker whose second entry is a different person each time
         * is one people mis-click.
         */
        if (!iAmHr) {
            userRepository.findByRoleCodes(HR_DESK).stream()
                    .filter(User::isEnabled)
                    .filter(u -> !u.getId().equals(requesterId))
                    .sorted(java.util.Comparator.comparing(
                            u -> u.getName() == null ? "" : u.getName(),
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(u -> {
                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", u.getId());
                        m.put("name", u.getName() + " (HR)");
                        m.put("code", u.getEmployeeCode());
                        m.put("designation", "HR");
                        map.putIfAbsent(u.getId(), m);
                    });
        }

        /*
         * The administrator's configuration, applied last.
         *
         * Last on purpose. Everything above encodes routing -- HR's own
         * requests go above HR, nobody appears in their own list -- and this
         * only narrows the result. A module with nothing configured is
         * unrestricted, so until somebody ticks a box this returns exactly what
         * it always did.
         */
        java.util.List<Long> permitted = approvalRecipients
                .filter("HELPDESK", map.keySet().stream()
                        .map(id -> userRepository.findById(id).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList())
                .stream().map(User::getId).toList();
        map.keySet().retainAll(new java.util.HashSet<>(permitted));

        return java.util.List.copyOf(map.values());
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

        // A copy to the CTO, whoever the ticket was addressed to.
        oversight.notifyCto(userId, "New support request " + saved.getTicketCode(),
                safeName(userId) + " raised: " + saved.getTitle(),
                "HELPDESK", "/helpdesk");
        /*
         * Who is told a request arrived.
         *
         * <p>Two things were wrong here. The picker offers a single "HR (code)"
         * entry, so a request addressed to HR reached exactly one account --
         * and if that person was away it sat unread while their colleagues on
         * the same desk, who can act on it perfectly well, never knew it
         * existed. And when nothing was addressed at all, nobody was notified:
         * the ticket was saved and simply never announced.
         *
         * <p>So: the addressed account first, then the rest of the HR desk when
         * it went to HR, and the whole desk when it went to nobody. The raiser
         * is never told about their own request.
         */
        java.util.Map<Long, User> targets = new java.util.LinkedHashMap<>();
        User addressed = saved.getAssignedTo() == null
                ? null
                : userRepository.findById(saved.getAssignedTo()).orElse(null);
        if (addressed != null) {
            targets.put(addressed.getId(), addressed);
        }
        if (addressed == null || isHrRole(addressed)) {
            userRepository.findByRoleCodes(HR_DESK).stream()
                    .filter(User::isEnabled)
                    .forEach(u -> targets.putIfAbsent(u.getId(), u));
        }
        targets.remove(userId);

        /*
         * Everybody on the desk is told, and the wording says which of them has
         * to do something about it.
         *
         * <p>The colleagues need to know because they share the queue -- a
         * request nobody mentions is one that waits until somebody happens to
         * refresh. But only the addressee can answer it, and a notification
         * that reads the same to all of them makes four people each assume one
         * of the others is handling it.
         *
         * <p>The text messages go only to the addressee. Waking somebody's
         * phone about work that is not theirs to do is how people learn to
         * ignore it.
         */
        Long addressedId = addressed == null ? null : addressed.getId();
        targets.values().forEach(staff -> {
            boolean forThem = staff.getId().equals(addressedId);
            notificationService.createAndPush(staff.getId(),
                    "New support request " + saved.getTicketCode(),
                    forThem
                            ? safeName(userId) + " sent you a support request"
                            : safeName(userId) + " raised a support request"
                                    + (addressedId == null
                                            ? " — nobody is named on it yet"
                                            : " for " + safeName(addressedId)),
                    "HELPDESK", "/helpdesk");
            if (forThem || addressedId == null) {
                sms(staff.getId(), safeName(userId) + " raised support request "
                        + saved.getTicketCode() + ": " + saved.getTitle());
            }
        });
        return toResponse(saved);
    }

    /**
     * The person who raised a ticket corrects it. Only while it is still OPEN --
     * once an agent has picked it up, the details they are working from should
     * not shift underneath them; a reply on the thread is the way to add to it.
     *
     * The code, the category and the status are not the raiser's to change.
     */
    /**
     * Withdraw a ticket you raised, while nobody has picked it up.
     *
     * <p>The row stays and its status becomes CANCELLED rather than being
     * deleted: an agent who has seen it in their queue should find out what
     * became of it instead of finding it gone.
     *
     * <p>The same two rules editing carries -- yours, and still OPEN. Once an
     * agent has started work the ticket is what they are working from, and
     * withdrawing it out from under them is not the raiser's to do.
     */
    @Transactional
    public void cancelOwn(Long userId, Long ticketId) {
        Ticket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Ticket"));
        if (!userId.equals(t.getRaisedBy())) {
            throw ApiException.business("You can only cancel tickets you raised");
        }
        if ("CANCELLED".equalsIgnoreCase(t.getStatus())) {
            throw ApiException.business("This ticket is already cancelled.");
        }
        if (!"OPEN".equalsIgnoreCase(t.getStatus())) {
            throw ApiException.business(
                    "This ticket is already " + t.getStatus().toLowerCase().replace('_', ' ')
                            + " — it can no longer be cancelled.");
        }
        t.setStatus("CANCELLED");
        ticketRepository.save(t);
    }

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
    /**
     * The accounts whose queue this person shares.
     *
     * <p>Just themselves, unless they are on the HR desk -- in which case the
     * whole desk, because the picker offers HR as one entry and which account
     * it happened to name is an accident of list order rather than a decision.
     */
    private java.util.List<Long> queueMates(Long viewerId) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        ids.add(viewerId);
        User me = userRepository.findById(viewerId).orElse(null);
        if (isHrRole(me)) {
            userRepository.findByRoleCodes(HR_DESK).forEach(u -> {
                if (!ids.contains(u.getId())) ids.add(u.getId());
            });
        }
        return ids;
    }

    public PageResponse<TicketResponse> agentQueue(Long agentId, String status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        String statusFilter = (status == null || status.isBlank()) ? null : status.toUpperCase();
        /*
         * "Assigned to me" means assigned to me.
         *
         * <p>Narrower than "All tickets" on purpose, and the two answer
         * different questions. The desk shares what it can see -- that is what
         * findForDesk is for, and it is what the All tab shows -- but a queue
         * headed "assigned to me" that lists a colleague's work is a queue
         * nobody can plan a day from. Somebody opening this tab is asking what
         * they personally have to answer.
         *
         * <p>The status filter is applied inside that scope. It used to call
         * findByStatus, which ignored who each ticket was addressed to, so
         * filtering an empty queue by "Open" made other people's tickets
         * appear -- a filter that widens what it is filtering.
         */
        Page<Ticket> result = ticketRepository.findAssignedToDesk(
                statusFilter, java.util.List.of(agentId), pageable);
        return PageResponse.from(result.map(this::toResponseNoComments));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> allTickets(Long viewerId, String status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        String statusFilter = (status == null || status.isBlank()) ? null : status.toUpperCase();
        /*
         * A system administrator sees every ticket -- they hold USER_MANAGE to
         * keep the portal running, and a queue they cannot see is one they
         * cannot fix. An agent sees what was addressed to them, plus what they
         * raised: a ticket names its recipient, and choosing the CTO over HR is
         * a real choice somebody makes for a reason.
         */
        // Keyed on the account, not on USER_MANAGE -- see ComplaintService.
        boolean seesEverything = oversight.seesEveryRequest(viewerId);
        Page<Ticket> result;
        if (seesEverything) {
            result = statusFilter != null
                    ? ticketRepository.findByStatusOrderByCreatedAtDesc(statusFilter, pageable)
                    : ticketRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            result = ticketRepository.findForDesk(
                    statusFilter, viewerId, queueMates(viewerId), pageable);
        }
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

        // And the status change, so oversight follows the whole life of a
        // ticket rather than only its arrival.
        oversight.notifyCto(actorId, "Ticket " + t.getTicketCode() + " "
                        + status.toLowerCase().replace('_', ' '),
                safeName(t.getRaisedBy()) + "'s ticket " + t.getTicketCode()
                        + " is now " + status + ".",
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
