package com.pixous.hrportal.modules.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A task assigned by an admin/HR to an employee. The employee marks it
 * complete; admins see everyone's tasks grouped per employee.
 */
@Getter
@Setter
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "assigned_to", nullable = false)
    private Long assignedTo;

    @Column(name = "assigned_by")
    private Long assignedBy;

    /** PENDING | COMPLETED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    /** LOW | MEDIUM | HIGH */
    @Column(nullable = false, length = 10)
    private String priority = "MEDIUM";

    /** Completion percentage 0–100, updated by the assignee. */
    @Column(nullable = false)
    private Integer progress = 0;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Set when the task is part of a team (designation) assignment; null for individual tasks. */
    @Column(name = "team_batch_id", length = 40)
    private String teamBatchId;

    @Column(name = "team_name", length = 150)
    private String teamName;

    /**
     * The day each kind of due-date reminder last went out. Null means never,
     * which is where a task starts; a date here stops the same nudge repeating.
     */
    @Column(name = "reminded_before")
    private LocalDate remindedBefore;
    @Column(name = "reminded_due")
    private LocalDate remindedDue;
    @Column(name = "reminded_overdue")
    private LocalDate remindedOverdue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
