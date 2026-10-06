package com.growdigitalbridge.payroll.calculation;

import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves "the compensation effective for this employee during this period" (Section G): the
 * one {@code EmployeeCompensation} row whose effective range **overlaps** the period
 * (`[period.startDate, period.endDate]`) - not merely a record already effective at the period's
 * first day (Payroll V1 completion review fix: a record becoming effective anywhere within the
 * period, e.g. a new joiner hired mid-month, now resolves correctly instead of being silently
 * skipped). Splitting/prorating a period across two compensation records is still not specially
 * handled - no documented rule defines how to, and none is invented here; if an employee has two
 * sequential records that each independently overlap the same period, resolution is deliberately
 * ambiguous (see {@code EmployeeCompensationRepository.findEffectiveForEmployee}'s Javadoc) rather
 * than guessing which one, or how to split between them. Never mutates or supersedes a historical
 * record (Section G: "do not overwrite historical compensation").
 */
@Component
public class CompensationResolver {

    private final EmployeeCompensationRepository repository;

    public CompensationResolver(EmployeeCompensationRepository repository) {
        this.repository = repository;
    }

    public Optional<EmployeeCompensation> resolveEffective(UUID employeeRef, PayrollPeriod period) {
        return repository.findEffectiveForEmployee(employeeRef, period.getStartDate(), period.getEndDate());
    }
}
