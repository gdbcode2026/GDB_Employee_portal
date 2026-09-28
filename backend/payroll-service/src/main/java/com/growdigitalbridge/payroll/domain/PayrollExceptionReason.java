package com.growdigitalbridge.payroll.domain;

/**
 * Why an employee in a run's snapshot produced no {@link PayrollRunLine}. Only one reason is
 * defined so far - Section I explicitly leaves "block the run vs. silently skip" unresolved for
 * this exact case (PAYROLL_REQUIREMENTS.md Section X); this enum exists so that decision, once
 * made, has a concrete record to act on rather than being invented here.
 */
public enum PayrollExceptionReason {
    NO_EFFECTIVE_COMPENSATION
}
