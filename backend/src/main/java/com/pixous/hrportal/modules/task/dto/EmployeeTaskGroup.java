package com.pixous.hrportal.modules.task.dto;

import java.util.List;

/**
 * All tasks for one employee, for the admin "All Employee Tasks" section
 * (grouped per person, split by industry in the UI).
 */
public record EmployeeTaskGroup(
        Long userId,
        String employeeName,
        String employeeCode,
        String industry,
        int totalTasks,
        int pendingTasks,
        int completedTasks,
        List<TaskResponse> tasks
) {}
