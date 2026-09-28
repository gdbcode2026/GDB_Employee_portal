package com.growdigitalbridge.payroll.calculation;

import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves "the compensation effective for this employee during this period" (Section G): the
 * one {@code EmployeeCompensation} row whose effective range covers the period. A period's
 * {@code startDate} is used as the reference point-in-time - compensation revisions taking effect
 * mid-period are not specially handled, since no documented rule defines how to split a period
 * across two compensation records; this is a flagged minimum decision, not an invented policy.
 * Never mutates or supersedes a historical record (Section G: "do not overwrite historical
 * compensation").
 */
@Component
public class CompensationResolver {

    private final EmployeeCompensationRepository repository;

    public CompensationResolver(EmployeeCompensationRepository repository) {
        this.repository = repository;
    }

    public Optional<EmployeeCompensation> resolveEffective(UUID employeeRef, PayrollPeriod period) {
        return repository.findEffectiveForEmployee(employeeRef, period.getStartDate());
    }
}
