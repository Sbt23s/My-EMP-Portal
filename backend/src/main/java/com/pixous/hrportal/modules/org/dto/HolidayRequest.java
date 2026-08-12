package com.pixous.hrportal.modules.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record HolidayRequest(
        @NotBlank String name,
        @NotNull LocalDate holidayDate
) {}
