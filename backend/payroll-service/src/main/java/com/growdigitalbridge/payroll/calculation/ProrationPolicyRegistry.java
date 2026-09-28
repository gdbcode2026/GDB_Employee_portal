package com.growdigitalbridge.payroll.calculation;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code CompensationComponent.prorationPolicyCode} to a registered {@link
 * ProrationPolicy}, falling back to {@link NoOpProrationPolicy} when the code is null or
 * unregistered - the same configuration-driven pattern as {@link CalculationStrategyRegistry}.
 */
@Component
public class ProrationPolicyRegistry {

    static final String NO_OP = "NO_OP";

    private final NoOpProrationPolicy noOpProrationPolicy;

    public ProrationPolicyRegistry(NoOpProrationPolicy noOpProrationPolicy) {
        this.noOpProrationPolicy = noOpProrationPolicy;
    }

    public BigDecimal resolveAndCompute(ComponentCalculationContext context) {
        String code = context.component().getProrationPolicyCode();
        ProrationPolicy policy = switch (code == null ? NO_OP : code) {
            case NO_OP -> noOpProrationPolicy;
            default -> noOpProrationPolicy;
        };
        return policy.computeAdjustment(context);
    }
}
