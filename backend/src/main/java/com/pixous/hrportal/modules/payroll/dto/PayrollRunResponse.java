package com.pixous.hrportal.modules.payroll.dto;

import com.pixous.hrportal.modules.payroll.PayrollRun;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One month's payroll run, as the Payroll Runs screen reads it.
 *
 * <p>The screen showed "undefined" for the month and ₹0.00 for both totals on
 * every card. Two separate causes, both of them this record's:
 *
 * <ul>
 *   <li>it emits {@code payMonth}/{@code payYear} and the card reads
 *       {@code runMonth}/{@code runYear}, so the heading rendered the word
 *       "undefined";
 *   <li>{@code totalEmployees}, {@code totalGross} and {@code totalNet} were
 *       never emitted at all — nothing summed the payslips — so the two figures
 *       a person actually reads a run card for were always zero.
 * </ul>
 *
 * <p>The frontend's TypeScript interface declared all five, which is why the
 * compiler never objected: it described a response the server does not send.
 *
 * <p>Both names are kept. {@code payMonth} because existing callers read it,
 * {@code runMonth} because that is what the screen asks for; they are the same
 * value, and removing either breaks one of the two.
 */
public record PayrollRunResponse(
        Long id,
        int payMonth,
        int payYear,
        /** Same value as payMonth, under the name the runs screen reads. */
        int runMonth,
        int runYear,
        String status,
        Long runBy,
        LocalDateTime runAt,
        Long financeApprovedBy,
        LocalDateTime financeApprovedAt,
        /** How many payslips this run produced. */
        int totalEmployees,
        /** Summed across those payslips, so the card and the list agree. */
        BigDecimal totalGross,
        BigDecimal totalNet,
        List<PayslipResponse> payslips
) {
    public static PayrollRunResponse from(PayrollRun run, List<PayslipResponse> payslips) {
        List<PayslipResponse> slips = payslips == null ? List.of() : payslips;
        // Nulls treated as zero rather than skipped: a payslip with no gross is
        // a payslip that contributed nothing, and dropping it from the count
        // would make the header disagree with the rows beneath it.
        BigDecimal gross = slips.stream()
                .map(p -> p.grossSalary() == null ? BigDecimal.ZERO : p.grossSalary())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = slips.stream()
                .map(p -> p.netPay() == null ? BigDecimal.ZERO : p.netPay())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PayrollRunResponse(
                run.getId(),
                run.getPayMonth(),
                run.getPayYear(),
                run.getPayMonth(),
                run.getPayYear(),
                run.getStatus(),
                run.getRunBy(),
                run.getRunAt(),
                run.getFinanceApprovedBy(),
                run.getFinanceApprovedAt(),
                slips.size(),
                gross,
                net,
                slips
        );
    }
}
