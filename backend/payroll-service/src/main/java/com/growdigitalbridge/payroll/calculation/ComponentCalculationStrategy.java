package com.growdigitalbridge.payroll.calculation;

import java.math.BigDecimal;

/**
 * Resolves the amount one {@code CompensationComponent} contributes for one employee/period.
 * Looked up by {@code CompensationComponent.calculationStrategyCode} through {@link
 * CalculationStrategyRegistry} - a named, swappable strategy, never a hard-coded formula. The
 * only implementation registered by this codebase is {@link FixedAmountStrategy}: no deduction,
 * tax, or statutory formula is implemented anywhere in Phase 2.
 */
public interface ComponentCalculationStrategy {

    BigDecimal computeAmount(ComponentCalculationContext context);
}
