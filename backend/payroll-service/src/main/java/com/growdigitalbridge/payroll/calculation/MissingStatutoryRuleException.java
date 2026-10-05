package com.growdigitalbridge.payroll.calculation;

/**
 * Thrown internally by {@code StatutoryRuleCalculationStrategy} when a {@code
 * CompensationComponent.calculationStrategyCode} names a statutory rule family that has no
 * {@code ACTIVE} version covering the period - i.e. configuration genuinely does not exist yet.
 * Never allowed to fail the whole run or the employee's line: {@code PayrollCalculationEngine}
 * catches this, treats the component's contribution as zero (never a fabricated amount), and
 * records a {@code MISSING_STATUTORY_RULE}/{@code TAX_CONFIGURATION_REQUIRED} {@code
 * PayrollException} instead (item 10).
 */
public class MissingStatutoryRuleException extends RuntimeException {

    private final String ruleCode;

    public MissingStatutoryRuleException(String ruleCode) {
        super("No active statutory rule found for code '" + ruleCode + "' covering this payroll period.");
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() { return ruleCode; }
}
