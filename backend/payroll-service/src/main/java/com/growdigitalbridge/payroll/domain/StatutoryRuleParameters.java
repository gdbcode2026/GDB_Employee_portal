package com.growdigitalbridge.payroll.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * The structured shape of {@code StatutoryRule.parameters} (stored as JSON) - the generic,
 * configuration-only inputs a {@link StatutoryRuleCalculationType} computation needs. No field
 * here is a legal value by itself; every field is empty/null until an approved configuration sets
 * it (Rule Engine task, items 2/17; brackets added by the India Payroll V1 architecture-extension
 * task). Which fields apply depends on the owning rule's {@code calculationType}:
 * <ul>
 *   <li>{@code amount} - {@code FIXED_AMOUNT} only.</li>
 *   <li>{@code percentage} - {@code PERCENTAGE}/{@code THRESHOLD_BASED}; 0-100.</li>
 *   <li>{@code wageBasisComponentCode} - the {@code PayComponent} code the percentage/bracket
 *   calculation applies to (e.g. a specific earning); {@code null} means "gross of all EARNING
 *   components." Used by every calculation type except {@code FIXED_AMOUNT}.</li>
 *   <li>{@code minWage} - {@code THRESHOLD_BASED} only; an eligibility floor - below this wage
 *   basis, the rule does not apply (surfaced as {@code STATUTORY_RULE_NOT_APPLICABLE}, never a
 *   fabricated amount).</li>
 *   <li>{@code maxWage} - {@code THRESHOLD_BASED} only; a wage ceiling the percentage is computed
 *   against (e.g. a contribution wage ceiling).</li>
 *   <li>{@code cap} - {@code THRESHOLD_BASED} only; a maximum resulting contribution amount.</li>
 *   <li>{@code brackets} - {@code SLAB_BASED}/{@code PROGRESSIVE_TAX} only; an ordered,
 *   contiguous, non-overlapping list of {@link StatutoryRuleBracket}s. Never an executable
 *   formula - only numeric bounds/amounts/percentages, validated for structural correctness at
 *   write time by {@code StatutoryRuleService}.</li>
 * </ul>
 */
public record StatutoryRuleParameters(
        BigDecimal amount,
        BigDecimal percentage,
        String wageBasisComponentCode,
        BigDecimal minWage,
        BigDecimal maxWage,
        BigDecimal cap,
        List<StatutoryRuleBracket> brackets) {
}
