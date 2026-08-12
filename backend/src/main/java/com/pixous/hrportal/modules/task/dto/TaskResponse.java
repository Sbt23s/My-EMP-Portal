package com.pixous.hrportal.modules.task.dto;

import com.pixous.hrportal.modules.task.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        Long assignedTo,
        String assigneeName,
        String assigneeCode,
        String assigneeIndustry,
        Long assignedBy,
        String assignerName,
        String status,
        String priority,
        Integer progress,
        LocalDate dueDate,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String teamBatchId,
        String teamName
) {
    public static TaskResponse from(Task t, String assigneeName, String assigneeCode,
                                    String assigneeIndustry, String assignerName) {
        return new TaskResponse(
                t.getId(), t.getTitle(), t.getDescription(),
                t.getAssignedTo(), assigneeName, assigneeCode, assigneeIndustry,
                t.getAssignedBy(), assignerName,
                t.getStatus(), t.getPriority(), t.getProgress(), t.getDueDate(), t.getCreatedAt(), t.getCompletedAt(),
                t.getTeamBatchId(), t.getTeamName());
    }
}
