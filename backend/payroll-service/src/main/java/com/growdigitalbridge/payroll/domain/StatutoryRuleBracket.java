package com.growdigitalbridge.payroll.domain;

import java.math.BigDecimal;

/**
 * One ordered income band within a {@code SLAB_BASED}/{@code PROGRESSIVE_TAX} {@link
 * StatutoryRule} (India Payroll V1 architecture-extension task). A rule's full bracket list must
 * be contiguous and non-overlapping, ordered by {@code order} ascending with strictly increasing
 * {@code lowerBound} values, and only the last bracket (by {@code order}) may leave {@code
 * upperBound} {@code null} (open-ended top bracket) - all validated at write time by {@code
 * StatutoryRuleService}, never trusted at calculation time alone. Exactly one of {@code
 * fixedAmount}/{@code percentage} must be set per bracket - never both, never neither, and never
 * an executable formula or expression string.
 *
 * <ul>
 *   <li>{@code order} - explicit ascending position, independent of array index, so storage/
 *   transport order never silently changes a bracket's meaning.</li>
 *   <li>{@code lowerBound}/{@code upperBound} - the basis range this bracket covers;
 *   {@code upperBound == null} means "and above" (only valid for the last bracket).</li>
 *   <li>{@code fixedAmount} - a flat fee for this bracket (e.g. a Professional-Tax-style slab).</li>
 *   <li>{@code percentage} - a rate applied to the basis (whole basis for {@code SLAB_BASED}; only
 *   this bracket's own span for {@code PROGRESSIVE_TAX}'s marginal accumulation).</li>
 * </ul>
 */
public record StatutoryRuleBracket(
        int order,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        BigDecimal fixedAmount,
        BigDecimal percentage) {
}
