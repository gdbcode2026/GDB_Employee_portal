package com.growdigitalbridge.payroll.domain;

/**
 * How a {@link StatutoryRule} version's {@code parameters} are interpreted (Rule Engine task,
 * item 2, extended by the India Payroll V1 architecture-extension task) - the generic calculation
 * *shape*, never a legal rate or slab itself:
 * <ul>
 *   <li>{@code FIXED_AMOUNT} - {@code parameters.amount} is the contribution/deduction, unchanged.</li>
 *   <li>{@code PERCENTAGE} - {@code parameters.percentage} of a wage basis (gross pay, or a
 *   specific named component via {@code wageBasisComponentCode}).</li>
 *   <li>{@code THRESHOLD_BASED} - the same percentage-of-basis calculation as {@code PERCENTAGE},
 *   additionally gated by an eligibility floor ({@code minWage}), a wage ceiling the percentage is
 *   applied against ({@code maxWage}), and/or a maximum contribution amount ({@code cap}).</li>
 *   <li>{@code SLAB_BASED} - {@code parameters.brackets} is an ordered, contiguous, non-overlapping
 *   list of income bands; exactly ONE matching bracket applies (by the wage basis falling within
 *   its {@code [lowerBound, upperBound)} range), contributing either that bracket's own
 *   {@code fixedAmount} or {@code percentage} of the basis - never cumulative. Fits a flat-fee
 *   slab tax such as Professional Tax.</li>
 *   <li>{@code PROGRESSIVE_TAX} - the same ordered-bracket shape as {@code SLAB_BASED}, but
 *   *every* bracket the basis reaches contributes its own share (the classic marginal-rate
 *   accumulation an income-tax calculation requires): each bracket taxes only the portion of the
 *   basis falling within its own span, and the contributions are summed.</li>
 * </ul>
 * See {@link StatutoryRuleParameters}/{@link StatutoryRuleBracket} for the exact field shape and
 * {@code com.growdigitalbridge.payroll.calculation.StatutoryRuleAmountCalculator} for the
 * (purely arithmetic, no-invented-value) computation each type performs. No slab boundary, rate,
 * or threshold is hard-coded anywhere in this codebase for any of the five types - every number
 * comes from a rule's own configured {@code parameters}, supplied only through the management API.
 */
public enum StatutoryRuleCalculationType {
    FIXED_AMOUNT,
    PERCENTAGE,
    THRESHOLD_BASED,
    SLAB_BASED,
    PROGRESSIVE_TAX
}
