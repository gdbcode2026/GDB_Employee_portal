package com.growdigitalbridge.payroll.domain;

/**
 * The statutory/tax category a {@link StatutoryRule} family belongs to (Rule Engine task, item
 * 1). Fixed to these five categories only - no rate, slab, or eligibility value is implied by
 * naming a category. {@code OTHER} exists so a future statutory rule category can be configured
 * without a code change, consistent with "support other future statutory rules."
 */
public enum StatutoryRuleType {
    PF,
    ESI,
    PROFESSIONAL_TAX,
    TDS,
    OTHER
}
