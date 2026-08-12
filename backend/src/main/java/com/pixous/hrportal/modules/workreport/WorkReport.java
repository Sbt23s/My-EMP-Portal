package com.pixous.hrportal.modules.workreport;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single day's work-log row (like the shared spreadsheet): date, project,
 * hours and a task description. Employees enter their own rows; HR & Admin can
 * view everyone's rows grouped per employee.
 */
@Getter
@Setter
@Entity
@Table(name = "work_reports")
public class WorkReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "project_name", nullable = false, length = 160)
    private String projectName;

    @Column(name = "work_hours", nullable = false)
    private BigDecimal workHours = BigDecimal.ZERO;

    @Column(name = "task_description", columnDefinition = "TEXT")
    private String taskDescription;

    /**
     * Comma-separated upload paths — screenshots, documents, spreadsheets, video
     * — stored the same way chat stores its attachments. Null when there are none.
     */
    @Column(columnDefinition = "TEXT")
    private String attachments;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
