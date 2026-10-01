package com.growdigitalbridge.payroll.domain;

/**
 * Whether a statutory scheme (PF/ESI/Professional Tax) applies to an employee. Deliberately a
 * tri-state, explicitly-set value - never inferred from the presence/absence of an identifier
 * (task instruction: "do not infer NOT_APPLICABLE merely because an identifier is missing").
 * {@code PENDING_VERIFICATION} exists so HR/Finance can record "we have not yet confirmed this"
 * as a distinct, honest state rather than defaulting to either extreme.
 */
public enum StatutoryApplicabilityStatus {
    APPLICABLE,
    NOT_APPLICABLE,
    PENDING_VERIFICATION
}
