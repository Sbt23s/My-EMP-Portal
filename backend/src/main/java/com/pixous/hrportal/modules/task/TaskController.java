package com.pixous.hrportal.modules.task;

import com.pixous.hrportal.common.ApiResponse;
import com.pixous.hrportal.modules.task.dto.EmployeeTaskGroup;
import com.pixous.hrportal.modules.task.dto.TaskRequest;
import com.pixous.hrportal.modules.task.dto.TaskResponse;
import com.pixous.hrportal.modules.task.dto.TaskUpdateRequest;
import com.pixous.hrportal.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService service;
    private final TaskChatService chatService;
    private final TaskWorkloadService workloadService;
    private final com.pixous.hrportal.common.StorageService storageService;

    // ---- The conversation on a task ----

    /**
     * The task's messages. Access is decided by the work: the assignee, whoever
     * assigned it, the Team Leader of that team, HR, the admin and the company
     * head. The service refuses everybody else.
     */
    @GetMapping("/{id}/messages")
    public ApiResponse<List<java.util.Map<String, Object>>> taskMessages(@PathVariable Long id) {
        return ApiResponse.ok(chatService.messages(id, SecurityUtils.currentUserId()));
    }

    /** Post a message, with or without files. */
    @PostMapping("/{id}/messages")
    public ApiResponse<java.util.Map<String, Object>> sendTaskMessage(
            @PathVariable Long id,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "files", required = false)
            org.springframework.web.multipart.MultipartFile[] files) {
        List<String> paths = new java.util.ArrayList<>();
        if (files != null) {
            for (org.springframework.web.multipart.MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    paths.add(storageService.store(file, "task-chat"));
                }
            }
        }
        return ApiResponse.ok(
                chatService.send(id, SecurityUtils.currentUserId(), content, paths));
    }

    /** How many messages each of these tasks carries — for the chat icon's badge. */
    @GetMapping("/messages/counts")
    public ApiResponse<java.util.Map<String, Long>> taskMessageCounts(@RequestParam List<Long> ids) {
        return ApiResponse.ok(chatService.counts(ids));
    }

    // ---- How much work each person is carrying ----

    @GetMapping("/workload")
    public ApiResponse<List<java.util.Map<String, Object>>> workload() {
        return ApiResponse.ok(workloadService.workload(SecurityUtils.currentUserId()));
    }

    // ---- Due-date reminders ----

    @GetMapping("/reminder/settings")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','TASK_VIEW_ALL','TASK_ASSIGN')")
    public ApiResponse<java.util.Map<String, Object>> taskReminderSettings() {
        return ApiResponse.ok(workloadService.settings());
    }

    @PutMapping("/reminder/settings")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','TASK_VIEW_ALL')")
    public ApiResponse<java.util.Map<String, Object>> saveTaskReminderSettings(
            @RequestBody java.util.Map<String, Object> body) {
        boolean on = !Boolean.FALSE.equals(body.get("enabled"));
        String time = body.get("time") == null ? "09:30" : String.valueOf(body.get("time"));
        Integer lead = body.get("leadDays") == null
                ? null : Integer.valueOf(String.valueOf(body.get("leadDays")));
        workloadService.saveSettings(on, time, lead);
        return ApiResponse.ok(workloadService.settings(), "Reminder settings saved");
    }

    /** Send today's due-date reminders now, without waiting for the hour. */
    @PostMapping("/reminder/send")
    @PreAuthorize("hasAnyAuthority('USER_MANAGE','TASK_VIEW_ALL')")
    public ApiResponse<java.util.Map<String, Object>> sendTaskReminders() {
        int sent = workloadService.runReminders();
        return ApiResponse.ok(java.util.Map.of("sent", sent),
                sent == 0 ? "Nothing is due a reminder today" : "Sent " + sent + " reminder(s)");
    }

    // ---- Employee: own tasks ----

    @GetMapping("/me")
    public ApiResponse<List<TaskResponse>> mine() {
        return ApiResponse.ok(service.mine(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TaskResponse> complete(@PathVariable Long id) {
        return ApiResponse.ok(service.complete(SecurityUtils.currentUserId(), id), "Task marked complete");
    }

    @PostMapping("/{id}/progress")
    public ApiResponse<TaskResponse> progress(@PathVariable Long id,
                                              @RequestBody java.util.Map<String, Integer> body) {
        int progress = body == null ? 0 : body.getOrDefault("progress", 0);
        return ApiResponse.ok(service.updateProgress(SecurityUtils.currentUserId(), id, progress),
                "Progress updated");
    }

    // ---- Admin / HR: assign & view everyone ----

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('TASK_ASSIGN')")
    public ApiResponse<TaskResponse> assign(@Valid @RequestBody TaskRequest req) {
        return ApiResponse.ok(service.assign(SecurityUtils.currentUserId(), req), "Task assigned");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('TASK_ASSIGN')")
    public ApiResponse<TaskResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody TaskUpdateRequest req) {
        return ApiResponse.ok(service.updateTask(SecurityUtils.currentUserId(), id, req),
                "Task updated");
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('TASK_ASSIGN') or hasAuthority('TASK_VIEW_ALL')")
    public ApiResponse<List<EmployeeTaskGroup>> everyone(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.everyone(industry, q));
    }

    /** Export tasks to Excel, filtered by industry + assigned-date range. */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('TASK_VIEW_ALL')")
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] bytes = service.exportExcel(industry, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tasks-export.xlsx")
                .body(new ByteArrayResource(bytes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.message("Task deleted");
    }

    /** Delete a whole team assignment (all member tasks sharing the batch id). */
    @DeleteMapping("/team/{batchId}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ApiResponse<Void> deleteTeamBatch(@PathVariable String batchId) {
        service.deleteTeamBatch(batchId);
        return ApiResponse.message("Team task deleted");
    }
}
