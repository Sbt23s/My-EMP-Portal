package com.pixous.hrportal.modules.task.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Edit of an existing task by the person who assigned it. The assignee never
 * changes here — reassigning means a new task.
 *
 * {@code status} is optional: PENDING, IN_PROGRESS or COMPLETED. When it is
 * sent, the task moves to that state and its progress follows, so whoever
 * assigned the work can correct a status the assignee left behind.
 */
public record TaskUpdateRequest(
        @NotBlank String title,
        String description,
        LocalDate dueDate,
        String priority,
        String status
) {}
