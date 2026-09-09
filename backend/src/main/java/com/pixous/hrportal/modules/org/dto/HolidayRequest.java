package com.pixous.hrportal.modules.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A holiday being added to the calendar.
 *
 * <p>{@code state} was missing here, and the Add Holiday form has always had a
 * field for it -- labelled "Type", offering National or Optional. It posted
 * under the name "type", which matched nothing, so Jackson dropped it and every
 * holiday HR entered was saved without the distinction they had just typed.
 * Optional on purpose: a holiday with no qualifier is a national one, and
 * refusing to save it for want of a label would be worse than the silence.
 */
public record HolidayRequest(
        @NotBlank String name,
        @NotNull LocalDate holidayDate,
        String state
) {}
