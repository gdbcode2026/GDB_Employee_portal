package com.growdigitalbridge.payroll.domain;

/**
 * A {@link PayrollException}'s resolution state. {@code OPEN} is a pure visibility/tracking
 * state, not a block on the run (Section I/X leaves blocking policy PENDING_GDB_APPROVAL); marking
 * one {@code RESOLVED} records that HR/Finance has reviewed and addressed it (e.g. backfilled a
 * missing identifier) - it does not retroactively recalculate the run or its lines.
 */
public enum PayrollExceptionStatus {
    OPEN,
    RESOLVED
}
