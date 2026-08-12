package com.pixous.hrportal.modules.workreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Create / update payload for a single work-report row. */
public record WorkReportRequest(
        @NotNull LocalDate workDate,
        @NotBlank String projectName,
        @NotNull BigDecimal workHours,
        String taskDescription
) {}
