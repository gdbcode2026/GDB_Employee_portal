package com.growdigitalbridge.payroll.domain;

/**
 * Section E/F document both values. Only {@code REGULAR} is reachable in this Phase 1
 * foundation - the adjustment/correction flow (Section K) that creates {@code ADJUSTMENT} runs
 * is explicitly out of this task's scope. The column exists now so a future phase can add
 * adjustment runs without a schema migration.
 */
public enum PayrollRunType {
    REGULAR,
    ADJUSTMENT
}
