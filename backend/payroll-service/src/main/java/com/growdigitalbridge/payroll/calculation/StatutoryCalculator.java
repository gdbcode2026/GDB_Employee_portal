package com.growdigitalbridge.payroll.calculation;

/**
 * Distinct extension point for statutory deduction/contribution components (PF, ESI,
 * Professional Tax, etc. - see the Common India Payroll V1 Baseline in
 * PAYROLL_REQUIREMENTS.md). Kept as its own interface, separate from a generic deduction, so a
 * real statutory rule can be registered per component in future without changing the engine's
 * control flow or any other component's calculation. No implementation of this interface exists
 * beyond {@link FixedAmountStrategy} registered under a statutory component's strategy code -
 * no rate, slab, or threshold is defined anywhere in this codebase.
 */
public interface StatutoryCalculator extends ComponentCalculationStrategy {
}
