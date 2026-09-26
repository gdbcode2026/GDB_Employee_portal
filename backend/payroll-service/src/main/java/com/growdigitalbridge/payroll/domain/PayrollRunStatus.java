package com.growdigitalbridge.payroll.domain;

/**
 * The exact state machine defined by PAYROLL_REQUIREMENTS.md Section D:
 * <pre>
 * DRAFT -&gt; CALCULATED -&gt; PENDING_APPROVAL -&gt; APPROVED -&gt; FINALIZED
 *                                          \-&gt; REJECTED (re-enters processing for correction)
 * </pre>
 * {@code CANCELLED} is documented ("DRAFT/CALCULATED/PENDING_APPROVAL -&gt; CANCELLED, before
 * finalization only") but no endpoint reaches it in this Phase 1 foundation - it is modeled here
 * for schema/enum completeness only, per this task's explicit scope (no cancel action was asked
 * for). FINALIZED is terminal: no service method ever updates a FINALIZED run's row (decision 8).
 */
public enum PayrollRunStatus {
    DRAFT,
    CALCULATED,
    PENDING_APPROVAL,
    APPROVED,
    FINALIZED,
    REJECTED,
    CANCELLED
}
