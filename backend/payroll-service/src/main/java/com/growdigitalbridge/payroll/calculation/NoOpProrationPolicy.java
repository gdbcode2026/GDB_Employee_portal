package com.growdigitalbridge.payroll.calculation;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * The only registered proration policy: always returns zero, regardless of attendance/leave
 * input, so full compensation is paid. Per Section H, this is the sole non-invented default -
 * paying full compensation is the absence of a rule, not the assertion of one. An actual
 * unpaid-leave/loss-of-pay formula may be registered under its own code in {@link
 * ProrationPolicyRegistry} once GDB Finance/Legal supplies it.
 */
@Component
public class NoOpProrationPolicy implements ProrationPolicy {

    @Override
    public BigDecimal computeAdjustment(ComponentCalculationContext context) {
        return BigDecimal.ZERO;
    }
}
