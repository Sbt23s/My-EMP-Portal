package com.pixous.hrportal.modules.discipline.dto;

import com.pixous.hrportal.modules.discipline.DisciplineRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DisciplineDtos {

    private DisciplineDtos() {
    }

    /** What HR sends to raise one. The reference code and status are not theirs to set. */
    public record CreateRequest(
            @NotNull Long employeeId,
            @NotNull LocalDate incidentDate,
            @NotBlank String disciplineType,
            String severity,
            @NotBlank String subject,
            @NotBlank String description,
            String actionTaken,
            String attachments
    ) {
    }

    /** An edit. The same fields, minus the employee: moving a record to somebody
     *  else would rewrite history rather than correct it. */
    public record UpdateRequest(
            @NotNull LocalDate incidentDate,
            @NotBlank String disciplineType,
            String severity,
            @NotBlank String subject,
            @NotBlank String description,
            String actionTaken,
            String attachments,
            String status
    ) {
    }

    /** The employee's side of it. */
    public record ResponseRequest(@NotBlank String response) {
    }

    /** The CTO's review: remarks the employee is shown, and where it leaves the record. */
    public record ReviewRequest(String remarks, String status) {
    }

    /**
     * One record as any of the three roles reads it.
     *
     * <p>Names are resolved here rather than left as ids, because every screen
     * that shows a record shows who it is about and who raised it, and doing it
     * per-screen means three places to get it wrong.
     */
    public record View(
            Long id,
            String referenceCode,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String department,
            Long reportedBy,
            String reportedByName,
            LocalDate incidentDate,
            String disciplineType,
            String severity,
            String subject,
            String description,
            String actionTaken,
            String attachments,
            String employeeResponse,
            LocalDateTime respondedAt,
            String ctoRemarks,
            String reviewedByName,
            LocalDateTime reviewedAt,
            String status,
            LocalDateTime createdAt
    ) {
        public static View of(DisciplineRecord d, String employeeName, String employeeCode,
                              String department, String reportedByName, String reviewedByName) {
            return new View(
                    d.getId(), d.getReferenceCode(), d.getEmployeeId(), employeeName, employeeCode,
                    department, d.getReportedBy(), reportedByName,
                    d.getIncidentDate(), d.getDisciplineType(), d.getSeverity(),
                    d.getSubject(), d.getDescription(), d.getActionTaken(), d.getAttachments(),
                    d.getEmployeeResponse(), d.getRespondedAt(),
                    d.getCtoRemarks(), reviewedByName, d.getReviewedAt(),
                    d.getStatus(), d.getCreatedAt()
            );
        }
    }
}
