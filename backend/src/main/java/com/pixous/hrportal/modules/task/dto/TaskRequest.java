package com.pixous.hrportal.modules.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Admin/HR payload to assign a task to an employee. */
public record TaskRequest(
        @NotBlank String title,
        String description,
        @NotNull Long assignedTo,
        LocalDate dueDate,
        String priority,
        // Set when this task is one member of a team (designation) assignment.
        String teamBatchId,
        String teamName
) {}
