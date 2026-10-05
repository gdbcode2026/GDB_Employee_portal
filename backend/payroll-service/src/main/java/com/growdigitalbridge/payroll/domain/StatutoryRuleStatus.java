package com.growdigitalbridge.payroll.domain;

/**
 * A {@link StatutoryRule} version's lifecycle state (Rule Engine task, item 7/8). {@code DRAFT}
 * is the only editable state - its parameters/effective dates may still change. {@code ACTIVE}
 * and {@code INACTIVE} are both immutable: once a version leaves {@code DRAFT}, correcting it
 * means creating a new version under the same {@code code}, never mutating this one - this is
 * what keeps a historical calculation reproducible even after a newer version is activated. Only
 * {@code ACTIVE} versions are ever resolved by the calculation engine (item 9); {@code DRAFT} and
 * {@code INACTIVE} versions are invisible to it.
 */
public enum StatutoryRuleStatus {
    DRAFT,
    ACTIVE,
    INACTIVE
}
