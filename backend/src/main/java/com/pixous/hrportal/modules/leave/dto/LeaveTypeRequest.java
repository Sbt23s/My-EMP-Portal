package com.pixous.hrportal.modules.leave.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Creating or editing a leave type.
 *
 * <p>The three flags are Boolean rather than boolean so an edit can leave them
 * alone. As primitives they defaulted to false whenever the caller omitted
 * them, and the edit form sends four fields -- so saving a change to a leave
 * type's name silently cleared carryForward, encashable and allowPastDates
 * along with every other setting it did not send.
 */
public record LeaveTypeRequest(
        @NotBlank String name,
        @NotBlank String code,
        Integer maxDaysPerYear,
        Boolean carryForward,
        Boolean encashable,
        String genderRestriction,
        Boolean allowPastDates,
        String accrualType,
        Integer minNoticeDays,
        Integer monthlyLimit,
        Boolean paid,
        /**
         * Whether the type is offered. Null leaves it as it is, so the create
         * form -- which has no such field -- keeps producing active types.
         */
        Boolean active
) {}
