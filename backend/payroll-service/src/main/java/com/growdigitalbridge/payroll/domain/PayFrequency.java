package com.growdigitalbridge.payroll.domain;

/**
 * Decision 3 locks monthly payroll periods; this task explicitly forbids inventing additional
 * payroll frequencies. {@code MONTHLY} is therefore the only value this enum defines - the
 * column exists (the task explicitly asks compensation records to "support pay frequency") but
 * is not a variable-frequency feature.
 */
public enum PayFrequency {
    MONTHLY
}
