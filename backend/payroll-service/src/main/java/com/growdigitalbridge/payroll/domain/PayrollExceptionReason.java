package com.growdigitalbridge.payroll.domain;

/**
 * Why an employee in a run's snapshot either produced no {@link PayrollRunLine}, or produced one
 * alongside a flagged configuration/data-quality gap. {@code NO_EFFECTIVE_COMPENSATION} is the
 * original Phase 2 reason (Section I/X) - kept unchanged for backward compatibility with existing
 * data/tests; it is functionally equivalent to "missing compensation." The remaining values are
 * configuration/data-quality exceptions (this task): unlike {@code NO_EFFECTIVE_COMPENSATION},
 * none of them skip the employee's {@link PayrollRunLine} - compensation still exists and is
 * still calculated identically; these only make a gap in supporting data *visible*, per the task
 * instruction not to silently ignore it. Whether any of these exception types should ever block a
 * run remains PENDING_GDB_APPROVAL (Section X) - recording one here is a visibility mechanism,
 * never an invented blocking rule.
 */
public enum PayrollExceptionReason {
    NO_EFFECTIVE_COMPENSATION,
    MISSING_STATUTORY_PROFILE,
    MISSING_PF_IDENTIFIER,
    MISSING_ESI_IDENTIFIER,
    INVALID_EFFECTIVE_DATES,
    OVERLAPPING_COMPENSATION,
    INVALID_PAY_COMPONENT,
    OTHER_CONFIGURATION_ERROR
}
