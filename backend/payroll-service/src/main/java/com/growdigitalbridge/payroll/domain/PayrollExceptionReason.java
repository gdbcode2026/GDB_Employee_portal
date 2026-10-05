package com.growdigitalbridge.payroll.domain;

/**
 * Why an employee in a run's snapshot either produced no {@link PayrollRunLine}, or produced one
 * alongside a flagged configuration/data-quality gap. {@code NO_EFFECTIVE_COMPENSATION} is the
 * original Phase 2 reason (Section I/X) - kept unchanged for backward compatibility with existing
 * data/tests; it is functionally equivalent to "missing compensation." The remaining values are
 * configuration/data-quality exceptions: unlike {@code NO_EFFECTIVE_COMPENSATION}, none of them
 * skip the employee's {@link PayrollRunLine} - compensation still exists and is still calculated
 * identically; these only make a gap in supporting data *visible*, per the task instruction not
 * to silently ignore it. Whether any of these exception types should ever block a run remains
 * PENDING_GDB_APPROVAL (Section X) - recording one here is a visibility mechanism, never an
 * invented blocking rule.
 *
 * <p>The five values added by the Configurable Statutory + Tax Rule Engine task are raised as
 * follows (see {@code PayrollCalculationEngine}):
 * <ul>
 *   <li>{@code MISSING_STATUTORY_RULE} - a non-TDS component's {@code calculationStrategyCode}
 *   names a rule family with no {@code ACTIVE} version covering the period.</li>
 *   <li>{@code TAX_CONFIGURATION_REQUIRED} - the same condition, but for a {@code TDS}-coded
 *   component specifically.</li>
 *   <li>{@code MISSING_TAX_CONFIGURATION} - a {@code TDS}-coded component exists on the
 *   employee's compensation but was never wired to any rule at all ({@code
 *   calculationStrategyCode} is null/blank). Deliberately narrower than "this employee has no
 *   TDS component" - the task explicitly forbids assuming every employee has a tax regime, so
 *   this never fires unless a TDS line was already configured.</li>
 *   <li>{@code STATUTORY_RULE_NOT_APPLICABLE} - an {@code ACTIVE} {@code THRESHOLD_BASED} rule
 *   was resolved but its configured {@code minWage} eligibility floor was not met - a correctly-
 *   zero contribution, not a missing one.</li>
 *   <li>{@code INVALID_STATUTORY_CONFIGURATION} - defined for completeness/documentation only;
 *   never actually raised as a stored exception by any runtime path. The states it would describe
 *   (overlapping rule versions, malformed parameters, invalid effective dates) are rejected at
 *   rule-creation/activation time by {@code StatutoryRuleService} instead, mirroring this
 *   codebase's existing {@code OVERLAPPING_COMPENSATION}/{@code INVALID_EFFECTIVE_DATES}/{@code
 *   INVALID_PAY_COMPONENT} precedent of write-time prevention over runtime detection.</li>
 * </ul>
 */
public enum PayrollExceptionReason {
    NO_EFFECTIVE_COMPENSATION,
    MISSING_STATUTORY_PROFILE,
    MISSING_PF_IDENTIFIER,
    MISSING_ESI_IDENTIFIER,
    INVALID_EFFECTIVE_DATES,
    OVERLAPPING_COMPENSATION,
    INVALID_PAY_COMPONENT,
    OTHER_CONFIGURATION_ERROR,
    MISSING_STATUTORY_RULE,
    MISSING_TAX_CONFIGURATION,
    INVALID_STATUTORY_CONFIGURATION,
    STATUTORY_RULE_NOT_APPLICABLE,
    TAX_CONFIGURATION_REQUIRED
}
