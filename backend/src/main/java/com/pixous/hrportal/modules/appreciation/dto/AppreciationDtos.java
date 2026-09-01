package com.pixous.hrportal.modules.appreciation.dto;

import com.pixous.hrportal.modules.appreciation.AppreciationLetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AppreciationDtos {

    private AppreciationDtos() {
    }

    /** What the issuer sends. The reference code and status are not theirs to set. */
    public record CreateRequest(
            @NotNull Long employeeId,
            @NotNull LocalDate letterDate,
            @NotBlank String achievement,
            @NotBlank String message,
            String template,
            /** False saves a draft; true sends it and tells the employee. */
            Boolean send
    ) {
    }

    /**
     * One letter as anybody reads it.
     *
     * <p>The employee's name, designation and the issuer's title are resolved
     * here rather than left as ids, because the letter itself is written in
     * terms of them -- the preview, the PDF and the table all need the same
     * three strings, and doing it per-screen means three places to get it
     * wrong.
     */
    public record View(
            Long id,
            String referenceCode,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String designation,
            String department,
            Long issuedBy,
            String issuedByName,
            String issuedByRole,
            LocalDate letterDate,
            String achievement,
            String message,
            String template,
            String status,
            LocalDateTime viewedAt,
            LocalDateTime downloadedAt,
            LocalDateTime createdAt
    ) {
        public static View of(AppreciationLetter a, String employeeName, String employeeCode,
                              String designation, String department,
                              String issuedByName, String issuedByRole) {
            return new View(
                    a.getId(), a.getReferenceCode(), a.getEmployeeId(),
                    employeeName, employeeCode, designation, department,
                    a.getIssuedBy(), issuedByName, issuedByRole,
                    a.getLetterDate(), a.getAchievement(), a.getMessage(),
                    a.getTemplate(), a.getStatus(),
                    a.getViewedAt(), a.getDownloadedAt(), a.getCreatedAt()
            );
        }
    }
}
