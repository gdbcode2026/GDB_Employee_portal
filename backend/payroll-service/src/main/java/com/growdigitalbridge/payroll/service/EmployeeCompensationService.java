package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.EmployeeCompensationDtos;
import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayComponent;
import com.growdigitalbridge.payroll.domain.PayFrequency;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import com.growdigitalbridge.payroll.repository.PayComponentRepository;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.payroll.service.exception.InvalidRequestException;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages {@link EmployeeCompensation}/{@link CompensationComponent} (Compensation Management
 * task, item 1). Never accepts an employee-self-service write path - every mutation here requires
 * {@code payroll.process}, enforced in {@code SecurityConfig}, not by this class. {@code
 * currency}/{@code payFrequency} are always the locked {@code INR}/{@code MONTHLY} values
 * (decisions 2/3), never accepted as request input.
 *
 * <p><b>Overlap prevention</b> (item 1): a new or updated record's effective range may never
 * overlap another {@code ACTIVE} record for the same employee, since {@link
 * com.growdigitalbridge.payroll.calculation.CompensationResolver} would otherwise find more than
 * one match for a given date and fail the entire run (an existing, deliberately-unchanged
 * behavior - see {@code PayrollCalculationEngine}).
 *
 * <p><b>Historical immutability</b> (item 1): once a record's effective range has actually been
 * used to calculate at least one {@code FINALIZED} run's line for that employee - i.e. a
 * finalized run's period start date falls within the record's {@code [effectiveFrom, effectiveTo]}
 * - no further update to it (or its components) is permitted. This is checked by walking the
 * employee's own {@link PayrollRunLine} history rather than a dedicated usage-tracking column,
 * since no such column exists and none is needed for this check's accuracy.
 */
@Service
public class EmployeeCompensationService {

    private final EmployeeCompensationRepository repository;
    private final CompensationComponentRepository componentRepository;
    private final PayComponentRepository payComponentRepository;
    private final PayrollRunLineRepository lineRepository;
    private final PayrollRunRepository runRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollAuditLog auditLog;

    public EmployeeCompensationService(EmployeeCompensationRepository repository,
                                        CompensationComponentRepository componentRepository,
                                        PayComponentRepository payComponentRepository,
                                        PayrollRunLineRepository lineRepository,
                                        PayrollRunRepository runRepository,
                                        PayrollPeriodRepository periodRepository,
                                        PayrollAuditLog auditLog) {
        this.repository = repository;
        this.componentRepository = componentRepository;
        this.payComponentRepository = payComponentRepository;
        this.lineRepository = lineRepository;
        this.runRepository = runRepository;
        this.periodRepository = periodRepository;
        this.auditLog = auditLog;
    }

    @Transactional
    public EmployeeCompensationDtos.Response create(EmployeeCompensationDtos.CreateRequest request, String actor, UUID correlationId) {
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());
        validateComponents(request.components());
        assertNoOverlap(request.employeeRef(), request.effectiveFrom(), request.effectiveTo(), null);

        Instant now = Instant.now();
        EmployeeCompensation compensation = new EmployeeCompensation(UUID.randomUUID(), request.employeeRef(),
                EmployeeCompensation.LOCKED_CURRENCY, PayFrequency.MONTHLY, request.effectiveFrom(), request.effectiveTo(), actor, now);
        repository.save(compensation);
        saveComponents(compensation.getId(), request.components(), actor, now);

        auditLog.compensationCreated(compensation.getId(), compensation.getEmployeeRef(), actor, correlationId);
        return toResponse(compensation);
    }

    @Transactional(readOnly = true)
    public EmployeeCompensationDtos.Response getById(UUID id, String actor, UUID correlationId) {
        EmployeeCompensation compensation = find(id);
        auditLog.sensitiveRead("compensation", id, actor, correlationId);
        return toResponse(compensation);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeCompensationDtos.Response> listByEmployee(UUID employeeRef, Pageable pageable) {
        return PageResponse.of(repository.findByEmployeeRef(employeeRef, pageable).map(this::toResponse));
    }

    @Transactional
    public EmployeeCompensationDtos.Response update(UUID id, EmployeeCompensationDtos.UpdateRequest request,
                                                      String actor, UUID correlationId) {
        EmployeeCompensation compensation = find(id);
        if (hasBeenUsedByFinalizedRun(compensation)) {
            throw new InvalidLifecycleTransitionException("Compensation " + id
                    + " has already been used by a finalized payroll run and can no longer be updated.");
        }
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());
        validateComponents(request.components());
        assertNoOverlap(compensation.getEmployeeRef(), request.effectiveFrom(), request.effectiveTo(), id);

        Instant now = Instant.now();
        compensation.update(request.effectiveFrom(), request.effectiveTo(), request.status(), actor, now);
        repository.save(compensation);

        componentRepository.deleteByCompensationId(id);
        componentRepository.flush();
        saveComponents(id, request.components(), actor, now);

        auditLog.compensationUpdated(id, compensation.getEmployeeRef(), actor, correlationId);
        return toResponse(compensation);
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new InvalidRequestException("effectiveTo must not be before effectiveFrom.");
        }
    }

    /** Every component code must reference an existing, active {@link PayComponent} of the matching type (item 2/4 - INVALID_PAY_COMPONENT). */
    private void validateComponents(List<EmployeeCompensationDtos.ComponentRequest> components) {
        for (EmployeeCompensationDtos.ComponentRequest component : components) {
            PayComponent catalogueEntry = payComponentRepository.findByCode(component.componentCode())
                    .orElseThrow(() -> new InvalidRequestException(
                            "Invalid pay component: '" + component.componentCode() + "' does not exist in the pay component catalogue."));
            if (!catalogueEntry.isActive()) {
                throw new InvalidRequestException("Invalid pay component: '" + component.componentCode() + "' is not active.");
            }
            if (catalogueEntry.getType() != component.componentType()) {
                throw new InvalidRequestException("Invalid pay component: '" + component.componentCode()
                        + "' is catalogued as " + catalogueEntry.getType() + ", not " + component.componentType() + ".");
            }
        }
    }

    /**
     * Rejects a new/updated effective range that overlaps any other {@code ACTIVE} record for the
     * same employee (item 1). {@code excludeId} is the record being updated, if any.
     */
    private void assertNoOverlap(UUID employeeRef, LocalDate effectiveFrom, LocalDate effectiveTo, UUID excludeId) {
        for (EmployeeCompensation other : repository.findByEmployeeRefOrderByEffectiveFromDesc(employeeRef)) {
            if (other.getStatus() != com.growdigitalbridge.payroll.domain.CompensationStatus.ACTIVE) {
                continue;
            }
            if (excludeId != null && other.getId().equals(excludeId)) {
                continue;
            }
            boolean disjoint = (effectiveTo != null && effectiveTo.isBefore(other.getEffectiveFrom()))
                    || (other.getEffectiveTo() != null && other.getEffectiveTo().isBefore(effectiveFrom));
            if (!disjoint) {
                throw new ConflictException("The requested effective range overlaps an existing active compensation record ("
                        + other.getId() + ") for this employee.");
            }
        }
    }

    /**
     * True if a {@code FINALIZED} run's period start date ever fell within this record's
     * effective range for this employee - i.e. {@code CompensationResolver} would have resolved
     * this exact record for that run.
     */
    private boolean hasBeenUsedByFinalizedRun(EmployeeCompensation compensation) {
        for (PayrollRunLine line : lineRepository.findByEmployeeRef(compensation.getEmployeeRef())) {
            PayrollRun run = runRepository.findById(line.getRunId()).orElse(null);
            if (run == null || run.getStatus() != PayrollRunStatus.FINALIZED) {
                continue;
            }
            PayrollPeriod period = periodRepository.findById(run.getPeriodId()).orElse(null);
            if (period == null) {
                continue;
            }
            LocalDate periodStart = period.getStartDate();
            boolean withinRange = !periodStart.isBefore(compensation.getEffectiveFrom())
                    && (compensation.getEffectiveTo() == null || !periodStart.isAfter(compensation.getEffectiveTo()));
            if (withinRange) {
                return true;
            }
        }
        return false;
    }

    private void saveComponents(UUID compensationId, List<EmployeeCompensationDtos.ComponentRequest> components, String actor, Instant now) {
        for (EmployeeCompensationDtos.ComponentRequest component : components) {
            componentRepository.save(new CompensationComponent(UUID.randomUUID(), compensationId, component.componentCode(),
                    component.componentType(), component.amount(), component.prorationPolicyCode(),
                    component.calculationStrategyCode(), actor, now));
        }
    }

    private EmployeeCompensation find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee compensation " + id + " was not found."));
    }

    private EmployeeCompensationDtos.Response toResponse(EmployeeCompensation compensation) {
        List<EmployeeCompensationDtos.ComponentResponse> components = componentRepository.findByCompensationId(compensation.getId())
                .stream()
                .map(c -> new EmployeeCompensationDtos.ComponentResponse(c.getId(), c.getComponentCode(), c.getComponentType(),
                        c.getAmount(), c.getProrationPolicyCode(), c.getCalculationStrategyCode()))
                .toList();
        return new EmployeeCompensationDtos.Response(compensation.getId(), compensation.getEmployeeRef(), compensation.getCurrency(),
                compensation.getPayFrequency().name(), compensation.getEffectiveFrom(), compensation.getEffectiveTo(),
                compensation.getStatus(), components, compensation.getCreatedAt(), compensation.getUpdatedAt());
    }
}
