package com.growdigitalbridge.payroll.calculation;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code CompensationComponent.calculationStrategyCode} to a registered {@link
 * ComponentCalculationStrategy}. This is the "configuration-driven" seam item 8 requires: which
 * strategy applies to a component is data (a code string on the row), never a code branch on
 * component type or name. {@code null}/{@code FIXED_AMOUNT} resolves to {@link
 * FixedAmountStrategy} exactly as before; any other code is treated as a statutory rule family
 * identifier and delegated to {@link StatutoryRuleCalculationStrategy} (Rule Engine task) - the
 * rule itself (not this registry) decides what the code means, so no new Java branch is needed
 * per statutory law.
 */
@Component
public class CalculationStrategyRegistry {

    static final String FIXED_AMOUNT = "FIXED_AMOUNT";

    private final FixedAmountStrategy fixedAmountStrategy;
    private final StatutoryRuleCalculationStrategy statutoryRuleCalculationStrategy;

    public CalculationStrategyRegistry(FixedAmountStrategy fixedAmountStrategy,
                                        StatutoryRuleCalculationStrategy statutoryRuleCalculationStrategy) {
        this.fixedAmountStrategy = fixedAmountStrategy;
        this.statutoryRuleCalculationStrategy = statutoryRuleCalculationStrategy;
    }

    public BigDecimal resolveAndCompute(ComponentCalculationContext context) {
        String code = context.component().getCalculationStrategyCode();
        if (code == null || FIXED_AMOUNT.equals(code)) {
            return fixedAmountStrategy.computeAmount(context);
        }
        return statutoryRuleCalculationStrategy.computeAmount(context);
    }
}
