package com.growdigitalbridge.payroll.domain;

/**
 * How a {@link StatutoryRule} version's {@code parameters} are interpreted (Rule Engine task,
 * item 2) - the generic calculation *shape*, never a legal rate or slab itself:
 * <ul>
 *   <li>{@code FIXED_AMOUNT} - {@code parameters.amount} is the contribution/deduction, unchanged.</li>
 *   <li>{@code PERCENTAGE} - {@code parameters.percentage} of a wage basis (gross pay, or a
 *   specific named component via {@code wageBasisComponentCode}).</li>
 *   <li>{@code THRESHOLD_BASED} - the same percentage-of-basis calculation as {@code PERCENTAGE},
 *   additionally gated by an eligibility floor ({@code minWage}), a wage ceiling the percentage is
 *   applied against ({@code maxWage}), and/or a maximum contribution amount ({@code cap}).</li>
 * </ul>
 * See {@link StatutoryRuleParameters} for the exact field shape and
 * {@code com.growdigitalbridge.payroll.calculation.StatutoryRuleAmountCalculator} for the
 * (purely arithmetic, no-invented-value) computation each type performs.
 */
public enum StatutoryRuleCalculationType {
    FIXED_AMOUNT,
    PERCENTAGE,
    THRESHOLD_BASED
}
