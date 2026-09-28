package com.growdigitalbridge.payroll.calculation;

import java.math.BigDecimal;

/**
 * PAYROLL_REQUIREMENTS.md Section H's "configurable proration policy": a named, versioned
 * strategy the engine looks up per attendance/leave-sensitive component and invokes to determine
 * a pay adjustment (e.g. for unpaid leave or loss-of-pay days). Returns an adjustment amount to
 * ADD to the component's base amount - negative for a reduction, zero for no adjustment. No
 * implementation in this codebase computes anything other than zero; see {@link
 * NoOpProrationPolicy}.
 */
public interface ProrationPolicy {

    BigDecimal computeAdjustment(ComponentCalculationContext context);
}
