package com.growdigitalbridge.payroll.domain;

/**
 * No period-status vocabulary is enumerated anywhere in PAYROLL_REQUIREMENTS.md (only PayrollRun
 * has a documented state machine, Section D). {@code OPEN} is the only value any code path in
 * this Phase 1 foundation ever sets or transitions to. {@code CLOSED} is modeled for schema
 * completeness against Section F's documented {@code status} column and to make "period
 * validation" (a run may only be created against an open period) meaningful, but no endpoint
 * closes a period in this phase - that trigger is not documented anywhere.
 */
public enum PayrollPeriodStatus {
    OPEN,
    CLOSED
}
