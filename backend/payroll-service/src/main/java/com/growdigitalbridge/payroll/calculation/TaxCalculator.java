package com.growdigitalbridge.payroll.calculation;

/**
 * Distinct extension point for tax-withholding components (TDS - see the Common India Payroll V1
 * Baseline in PAYROLL_REQUIREMENTS.md). Kept as its own interface, separate from a generic
 * deduction or {@link StatutoryCalculator}, so a real tax-slab rule can be registered per
 * component in future without changing the engine's control flow. No implementation of this
 * interface exists beyond {@link FixedAmountStrategy} registered under a tax component's
 * strategy code - no slab, threshold, or eligibility rule is defined anywhere in this codebase.
 */
public interface TaxCalculator extends ComponentCalculationStrategy {
}
