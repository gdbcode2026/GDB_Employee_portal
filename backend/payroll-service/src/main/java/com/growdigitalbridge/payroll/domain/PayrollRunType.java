package com.growdigitalbridge.payroll.domain;

/**
 * Section E/F document both values. {@code REGULAR} is created via {@code POST /payroll/runs};
 * {@code ADJUSTMENT} is created via {@code POST /payroll/runs/{id}/adjustments} (Section K) against
 * an already-{@code FINALIZED} original run, referenced by that run's {@code corrects_run_id}.
 */
public enum PayrollRunType {
    REGULAR,
    ADJUSTMENT
}
