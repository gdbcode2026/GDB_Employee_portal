package com.growdigitalbridge.payroll.calculation;

/**
 * Thrown internally by {@code StatutoryRuleCalculationStrategy} when an {@code ACTIVE} statutory
 * rule *was* resolved but its own configured eligibility condition (a {@code THRESHOLD_BASED}
 * rule's {@code minWage}) was not met for this employee/period - a correctly-zero contribution,
 * distinct from a genuinely missing rule. {@code PayrollCalculationEngine} catches this, treats
 * the component's contribution as zero, and records a {@code STATUTORY_RULE_NOT_APPLICABLE}
 * {@code PayrollException} for visibility (item 10) - never a block on the run.
 */
public class StatutoryRuleNotApplicableException extends RuntimeException {

    private final String ruleCode;

    public StatutoryRuleNotApplicableException(String ruleCode) {
        super("Statutory rule '" + ruleCode + "' resolved but its eligibility condition was not met for this employee/period.");
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() { return ruleCode; }
}
