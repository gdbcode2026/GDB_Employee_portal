package com.growdigitalbridge.payroll.calculation;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code CompensationComponent.calculationStrategyCode} to a registered {@link
 * ComponentCalculationStrategy}, falling back to {@link FixedAmountStrategy} when the code is
 * null or unregistered. This is the "configuration-driven" seam item 8 requires: which strategy
 * applies to a component is data (a code string on the row), never a code branch on component
 * type or name. Only one strategy is registered today; new ones can be added here by name without
 * touching the calculation engine.
 */
@Component
public class CalculationStrategyRegistry {

    static final String FIXED_AMOUNT = "FIXED_AMOUNT";

    private final FixedAmountStrategy fixedAmountStrategy;

    public CalculationStrategyRegistry(FixedAmountStrategy fixedAmountStrategy) {
        this.fixedAmountStrategy = fixedAmountStrategy;
    }

    public BigDecimal resolveAndCompute(ComponentCalculationContext context) {
        String code = context.component().getCalculationStrategyCode();
        ComponentCalculationStrategy strategy = switch (code == null ? FIXED_AMOUNT : code) {
            case FIXED_AMOUNT -> fixedAmountStrategy;
            default -> fixedAmountStrategy;
        };
        return strategy.computeAmount(context);
    }
}
