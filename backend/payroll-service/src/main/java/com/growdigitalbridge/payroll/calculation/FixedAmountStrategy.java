package com.growdigitalbridge.payroll.calculation;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * The only registered calculation strategy in this codebase: returns the {@code
 * CompensationComponent}'s own configured, fixed {@code amount} unchanged, for every component
 * type (earning, deduction, employer contribution) and every marker interface ({@link
 * StatutoryCalculator}, {@link TaxCalculator}). This is the sole non-invented default, mirroring
 * {@code NoOpProrationPolicy}'s precedent (Section H): a fixed configured value is the absence of
 * a formula, not the assertion of one. A real rate/slab/threshold-driven strategy may be
 * registered under its own code in {@link CalculationStrategyRegistry} once GDB/Finance/Legal
 * supplies it - the pipeline's control flow does not change when that happens.
 */
@Component
public class FixedAmountStrategy implements StatutoryCalculator, TaxCalculator {

    @Override
    public BigDecimal computeAmount(ComponentCalculationContext context) {
        return context.component().getAmount();
    }
}
