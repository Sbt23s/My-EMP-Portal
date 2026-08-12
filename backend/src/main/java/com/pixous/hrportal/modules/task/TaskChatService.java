package com.pixous.hrportal.modules.task;

import com.pixous.hrportal.common.ApiException;
import com.pixous.hrportal.modules.notification.NotificationService;
import com.pixous.hrportal.modules.user.User;
import com.pixous.hrportal.modules.user.UserRepository;
import com.pixous.hrportal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The conversation on a task.
 *
 * <p>Who may take part is decided by the work, not by rank: the person doing it,
 * the person who assigned it, whoever runs the portal, and the Team Leader of
 * the assignee's team. Everybody else has no business in it — a task chat that
 * the whole company can read is not somewhere anyone will admit to being stuck.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskChatService {

    /** The company head, recognised by his employee code whatever roles he holds. */
    private static final String COMPANY_HEAD_CODE = "PIX-E100";

    private final TaskRepository taskRepository;
    private final TaskMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<Map<String, Object>> messages(Long taskId, Long requesterId) {
        Task task = task(taskId);
        assertCanTake(task, requesterId);

        List<TaskMessage> rows = messageRepository.findByTaskIdOrderBySentAtAsc(taskId);
        Map<Long, User> senders = userRepository.findAllById(
                        rows.stream().map(TaskMessage::getSenderId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> out = new ArrayList<>();
        for (TaskMessage m : rows) out.add(payload(m, senders.get(m.getSenderId())));
        return out;
    }

    /** How many messages each of these tasks carries, for the chat icon's badge. */
    @Transactional(readOnly = true)
    public Map<String, Long> counts(List<Long> taskIds) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (taskIds == null || taskIds.isEmpty()) return out;
        messageRepository.findByTaskIdIn(taskIds).forEach(m ->
                out.merge(String.valueOf(m.getTaskId()), 1L, Long::sum));
        return out;
    }

    // ---- writing ----

    @Transactional
    public Map<String, Object> send(Long taskId, Long senderId, String content, List<String> paths) {
        Task task = task(taskId);
        assertCanTake(task, senderId);

        String text = content == null ? "" : content.trim();
        boolean hasFiles = paths != null && !paths.isEmpty();
        if (text.isEmpty() && !hasFiles) {
            throw ApiException.business("Type something, or attach a file.");
        }

        TaskMessage m = new TaskMessage();
        m.setTaskId(taskId);
        m.setSenderId(senderId);
        m.setContent(text.isEmpty() ? null : text);
        m.setAttachments(hasFiles ? String.join(",", paths) : null);
        m.setSentAt(LocalDateTime.now());
        TaskMessage saved = messageRepository.save(m);

        User sender = userRepository.findById(senderId).orElse(null);
        Map<String, Object> body = payload(saved, sender);

        // Live, to anybody with the task open.
        try {
            messagingTemplate.convertAndSend("/topic/tasks/" + taskId, body);
        } catch (Exception e) {
            log.debug("Could not broadcast task message {}", saved.getId(), e);
        }

        // And a notification for the other side, so it is not missed when closed.
        String senderName = sender != null ? sender.getName() : "Someone";
        String preview = text.isEmpty() ? "sent a file" : text;
        notifyOthers(task, senderId, senderName, preview);

        return body;
    }

    // ---- who may take part ----

    /** True when this person may read and write on the task. */
    @Transactional(readOnly = true)
    public boolean canTakePart(Long taskId, Long userId) {
        try {
            assertCanTake(task(taskId), userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void assertCanTake(Task task, Long userId) {
        if (userId == null) throw ApiException.business("Sign in first.");
        if (userId.equals(task.getAssignedTo())) return;
        if (userId.equals(task.getAssignedBy())) return;
        if (SecurityUtils.hasAuthority("USER_MANAGE")) return;
        if (SecurityUtils.hasAuthority("TASK_VIEW_ALL")) return;

        User me = userRepository.findById(userId).orElse(null);
        if (me == null) throw ApiException.business("Sign in first.");
        if (COMPANY_HEAD_CODE.equalsIgnoreCase(String.valueOf(me.getEmployeeCode()))) return;

        // A Team Leader belongs in their own team's tasks, including ones they
        // did not assign themselves.
        boolean leader = me.getRoles().stream().anyMatch(r -> "IT_TL".equals(r.getCode()));
        if (leader) {
            User assignee = userRepository.findById(task.getAssignedTo()).orElse(null);
            String mine = me.getDesignationTitle();
            String theirs = assignee == null ? null : assignee.getDesignationTitle();
            if (mine != null && !mine.isBlank() && theirs != null
                    && mine.trim().equalsIgnoreCase(theirs.trim())) {
                return;
            }
        }
        throw ApiException.business("This conversation belongs to the people working on the task.");
    }

    /**
     * Tells the other side. The assignee and the person who assigned the task are
     * both told, minus whoever just spoke — being notified of your own message is
     * the fastest way to make people mute a feature.
     */
    private void notifyOthers(Task task, Long senderId, String senderName, String preview) {
        java.util.Set<Long> recipients = new java.util.LinkedHashSet<>();
        if (task.getAssignedTo() != null) recipients.add(task.getAssignedTo());
        if (task.getAssignedBy() != null) recipients.add(task.getAssignedBy());
        recipients.remove(senderId);

        for (Long id : recipients) {
            try {
                notificationService.createAndPush(
                        id,
                        "Task: " + task.getTitle(),
                        senderName + ": " + (preview.length() > 90 ? preview.substring(0, 90) + "…" : preview),
                        "TASK",
                        "/tasks?chat=" + task.getId());
            } catch (Exception e) {
                log.debug("Could not notify {} about task message", id, e);
            }
        }
    }

    // ---- plumbing ----

    private Task task(Long taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> ApiException.notFound("Task"));
    }

    private Map<String, Object> payload(TaskMessage m, User sender) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("taskId", m.getTaskId());
        out.put("senderId", m.getSenderId());
        out.put("senderName", sender != null ? sender.getName() : "Unknown");
        out.put("senderCode", sender != null ? sender.getEmployeeCode() : null);
        out.put("content", m.getContent());
        out.put("attachments", m.getAttachments());
        out.put("sentAt", m.getSentAt());
        return out;
    }
}
