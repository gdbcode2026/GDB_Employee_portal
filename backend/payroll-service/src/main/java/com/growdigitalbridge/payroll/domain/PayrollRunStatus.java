package com.growdigitalbridge.payroll.domain;

/**
 * The state machine defined by PAYROLL_REQUIREMENTS.md Section D, extended by Phase 2
 * (Calculation Core) with an explicit in-progress state:
 * <pre>
 * DRAFT -&gt; PROCESSING -&gt; CALCULATED -&gt; PENDING_APPROVAL -&gt; APPROVED -&gt; FINALIZED
 *                    \-&gt; CALCULATION_FAILED (reprocessable, see PayrollCalculationEngine)
 *                                          \-&gt; REJECTED (re-enters processing for correction)
 * </pre>
 * {@code PROCESSING} is a durable, separately-committed state so a run visibly shows "in
 * progress" for the duration of calculation, independent of whether calculation ultimately
 * succeeds or fails. {@code CALCULATION_FAILED} exists so a failed attempt is safely recorded
 * (decision 14/Section R) rather than silently reverting - {@code process} accepts it as a valid
 * starting point exactly like {@code DRAFT}/{@code REJECTED}, so reprocessing is just calling the
 * same endpoint again. {@code CANCELLED} is documented ("DRAFT/CALCULATED/PENDING_APPROVAL -&gt;
 * CANCELLED, before finalization only") but no endpoint reaches it - modeled for schema/enum
 * completeness only, since no cancel action is in scope. FINALIZED is terminal: no service method
 * ever updates a FINALIZED run's row (decision 8).
 */
public enum PayrollRunStatus {
    DRAFT,
    PROCESSING,
    CALCULATED,
    CALCULATION_FAILED,
    PENDING_APPROVAL,
    APPROVED,
    FINALIZED,
    REJECTED,
    CANCELLED
}
