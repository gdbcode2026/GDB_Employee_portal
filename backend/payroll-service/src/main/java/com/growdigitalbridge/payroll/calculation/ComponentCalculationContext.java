package com.growdigitalbridge.payroll.calculation;

import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayrollAttendanceInput;
import com.growdigitalbridge.payroll.domain.PayrollLeaveInput;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import java.util.List;

/**
 * Everything a {@link ComponentCalculationStrategy} or {@link ProrationPolicy} needs to compute
 * one component's amount for one employee in one run - never anything beyond that. Carries the
 * employee's finalized-attendance and approved-leave snapshots (Section I) so a real proration
 * formula, once supplied, has the inputs it needs without this context changing shape.
 */
public record ComponentCalculationContext(
        PayrollPeriod period,
        EmployeeCompensation compensation,
        CompensationComponent component,
        List<PayrollAttendanceInput> attendanceInputs,
        List<PayrollLeaveInput> leaveInputs) {
}
