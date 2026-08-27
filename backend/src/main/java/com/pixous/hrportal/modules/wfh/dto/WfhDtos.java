package com.pixous.hrportal.modules.wfh.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Payloads and views for the Work From Home module. */
public final class WfhDtos {

    private WfhDtos() {}

    /** Applying. */
    public record ApplyRequest(
            @NotNull(message = "Choose a start date")
            LocalDate fromDate,

            @NotNull(message = "Choose an end date")
            LocalDate toDate,

            @Size(max = 1000)
            String reason,

            @Size(max = 1000)
            String remarks,

            /**
             * Who it goes to. Optional in the payload — the server resolves the
             * rung from the applicant's own role, so a client that sends
             * nothing still gets it right, and a client that sends the wrong
             * person is corrected rather than obeyed.
             */
            Long requestedTo
    ) {}

    /** Approving or rejecting. */
    public record DecisionRequest(
            @NotNull(message = "Say whether this is approved or rejected")
            Boolean approve,

            /** Required to reject, optional to approve. */
            @Size(max = 1000)
            String comment
    ) {}

    /**
     * One request, as every screen sees it.
     *
     * <p>Carries the names as well as the ids so a table needs no second
     * lookup, and {@code canAct} so a client never has to work out for itself
     * whether the person reading may decide it.
     */
    public record WfhView(
            Long id,
            Long userId,
            String employeeName,
            String employeeCode,
            String team,
            String designation,
            /** Employee | Team Leader | HR | CTO — the rung, for the board. */
            String roleLabel,

            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal workingDays,
            String reason,
            String remarks,

            /** PENDING | APPROVED | REJECTED | CANCELLED | COMPLETED */
            String status,

            Long requestedTo,
            String requestedToName,
            String requestedToRole,

            Long decidedBy,
            String decidedByName,
            LocalDateTime decidedAt,
            String decisionComment,

            LocalDateTime createdAt,

            /** Whether the person reading this may decide it. */
            boolean canAct,
            /** Whether the person reading this may withdraw it. */
            boolean canCancel
    ) {}
}
