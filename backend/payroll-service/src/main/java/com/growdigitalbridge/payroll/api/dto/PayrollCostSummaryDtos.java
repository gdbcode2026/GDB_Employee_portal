package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.domain.PayrollRunType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PayrollCostSummaryDtos {

    private PayrollCostSummaryDtos() { }

    /**
     * One row per payroll run (Reporting V1 D3, Payroll Cost Summary). A dedicated response type,
     * deliberately not a reuse of {@link PayrollRunDtos.Response}, so the general run
     * list/detail contract is never affected by this report's fields.
     *
     * <p>{@code runType}/{@code correctsRunId} are included, never collapsed, because REGULAR and
     * ADJUSTMENT runs are never merged or netted here - each FINALIZED (or explicitly filtered)
     * run is always its own row (GDB decision, D3 Phase 1 review). {@code totalGrossPay}/{@code
     * totalDeductions}/{@code totalEmployerContributions}/{@code totalNetPay} are each a straight
     * {@code sum(...)} of the already-materialized {@code PayrollRunLine} column of the same name
     * for this run - no formula is computed or invented, and no combined "total payroll cost"
     * field exists (none is documented anywhere in this codebase; see the requirements doc).
     *
     * <p>No employee-level field appears anywhere in this record: {@code employeeCount} is a
     * count, every money field is a run-level sum, and nothing here identifies an individual
     * employee's salary, deductions, payslip, bank, or statutory data.
     */
    public record Response(UUID runId, UUID periodId, int periodYear, int periodMonth, PayrollRunType runType,
                            UUID correctsRunId, PayrollRunStatus status, int employeeCount, BigDecimal totalGrossPay,
                            BigDecimal totalDeductions, BigDecimal totalEmployerContributions, BigDecimal totalNetPay,
                            Instant finalizedAt) { }
}
