package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.calculation.CalculationResult;
import com.growdigitalbridge.payroll.calculation.PayrollCalculationEngine;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollPeriodStatus;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.domain.PayrollRunType;
import com.growdigitalbridge.payroll.repository.PayrollExceptionRepository;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.service.exception.CalculationFailedException;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.payroll.service.exception.InvalidRequestException;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns {@link PayrollRun} creation and the Section D state machine, including the Phase 2
 * calculation lifecycle: DRAFT/REJECTED/CALCULATION_FAILED -&gt; PROCESSING -&gt; CALCULATED (or -&gt;
 * CALCULATION_FAILED on error) -&gt; PENDING_APPROVAL -&gt; APPROVED -&gt; FINALIZED.
 *
 * <p>{@code process} is deliberately NOT itself {@code @Transactional}: it orchestrates three
 * separate transactions so that (a) the PROCESSING status commits and is visible for the
 * duration of calculation, (b) {@link PayrollCalculationEngine#calculate} runs in its own
 * transaction that rolls back completely on any failure (no partial {@code PayrollRunLine}s -
 * item 9), and (c) a calculation failure is still safely recorded as {@code CALCULATION_FAILED}
 * in a transaction of its own, independent of (and after) that rollback. {@code
 * TransactionTemplate} is used for (a)/(c) specifically so this still works correctly regardless
 * of caller context, without relying on Spring proxy self-invocation across methods on this same
 * bean.
 *
 * <p>Self-approval prevention (Section J): approve/reject/finalize all reject an attempt where
 * the acting identity equals the run's own {@code initiatedBy}, regardless of whether that
 * identity also holds {@code payroll.approve}.
 *
 * <p>Only {@code finalizeRun} produces a domain event: {@code payroll.processed.v1}
 * (PAYROLL_PROCESSED), with the exact payload Section Q already locks - run/period ID and
 * employee count. {@code payslip.generated.v1} is never produced here; it requires the {@code
 * Payslip} entity, which does not exist until Phase 4.
 */
@Service
public class PayrollRunService {

    private static final Set<PayrollRunStatus> REPROCESSABLE =
            EnumSet.of(PayrollRunStatus.DRAFT, PayrollRunStatus.REJECTED, PayrollRunStatus.CALCULATION_FAILED);

    private final PayrollRunRepository repository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollRunLineRepository lineRepository;
    private final PayrollExceptionRepository exceptionRepository;
    private final EmployeeClient employeeClient;
    private final PayrollCalculationEngine calculationEngine;
    private final OutboxEventWriter outboxEventWriter;
    private final PayrollAuditLog auditLog;
    private final TransactionTemplate transactionTemplate;

    public PayrollRunService(PayrollRunRepository repository, PayrollPeriodRepository periodRepository,
                              PayrollRunLineRepository lineRepository, PayrollExceptionRepository exceptionRepository,
                              EmployeeClient employeeClient, PayrollCalculationEngine calculationEngine,
                              OutboxEventWriter outboxEventWriter, PayrollAuditLog auditLog,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.periodRepository = periodRepository;
        this.lineRepository = lineRepository;
        this.exceptionRepository = exceptionRepository;
        this.employeeClient = employeeClient;
        this.calculationEngine = calculationEngine;
        this.outboxEventWriter = outboxEventWriter;
        this.auditLog = auditLog;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public PayrollRunDtos.Response create(PayrollRunDtos.CreateRequest request, String actor, UUID correlationId) {
        PayrollPeriod period = periodRepository.findById(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period " + request.periodId() + " was not found."));
        if (period.getStatus() != PayrollPeriodStatus.OPEN) {
            throw new InvalidRequestException("Payroll period " + period.getId() + " is not open.");
        }
        repository.findByPeriodIdAndRunTypeAndCorrectsRunId(period.getId(), PayrollRunType.REGULAR, null).ifPresent(existing -> {
            throw new ConflictException("A payroll run already exists for period " + period.getId() + ".");
        });

        Instant now = Instant.now();
        PayrollRun run = new PayrollRun(UUID.randomUUID(), period.getId(), employeeClient.resolveActiveEmployeeRefs(), actor, now);
        repository.save(run);
        auditLog.runCreated(run.getId(), actor, correlationId);
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public PayrollRunDtos.Response getById(UUID id, String actor, UUID correlationId) {
        PayrollRun run = find(id);
        auditLog.sensitiveRead("run", id, actor, correlationId);
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public com.growdigitalbridge.payroll.api.dto.PageResponse<PayrollRunDtos.Response> list(Pageable pageable) {
        Page<PayrollRun> page = repository.findAll(pageable);
        return com.growdigitalbridge.payroll.api.dto.PageResponse.of(page.map(this::toResponse));
    }

    /**
     * Orchestrates DRAFT/REJECTED/CALCULATION_FAILED -&gt; PROCESSING -&gt; CALCULATED, or -&gt;
     * CALCULATION_FAILED on error. See the class Javadoc for why this method itself is not
     * {@code @Transactional}.
     */
    public PayrollRunDtos.Response process(UUID id, String actor, UUID correlationId) {
        PayrollRunStatus currentStatus = find(id).getStatus();
        if (!REPROCESSABLE.contains(currentStatus)) {
            throw new InvalidLifecycleTransitionException("Payroll run " + id + " cannot be processed from status " + currentStatus + ".");
        }

        transactionTemplate.executeWithoutResult(status -> {
            PayrollRun run = find(id);
            run.startProcessing(actor, Instant.now());
            repository.save(run);
        });
        auditLog.processingStarted(id, actor, correlationId);

        try {
            CalculationResult result = calculationEngine.calculate(id, actor, correlationId);
            auditLog.calculationCompleted(id, actor, correlationId, result.lineCount(), result.exceptionCount());
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> {
                PayrollRun run = find(id);
                run.markCalculationFailed(actor, Instant.now());
                repository.save(run);
            });
            auditLog.calculationFailed(id, actor, correlationId, e.getClass().getSimpleName(), e.getMessage());
            throw new CalculationFailedException("Payroll run " + id + " calculation failed and was not finalized; see audit log.");
        }

        return toResponse(find(id));
    }

    @Transactional
    public PayrollRunDtos.Response submitForApproval(UUID id, String actor, UUID correlationId) {
        PayrollRun run = find(id);
        if (run.getStatus() != PayrollRunStatus.CALCULATED) {
            throw new InvalidLifecycleTransitionException("Payroll run " + id + " cannot be submitted for approval from status " + run.getStatus() + ".");
        }
        run.submitForApproval(actor, Instant.now());
        auditLog.runSubmittedForApproval(run.getId(), actor, correlationId);
        return toResponse(run);
    }

    @Transactional
    public PayrollRunDtos.Response approve(UUID id, String actor, UUID correlationId) {
        PayrollRun run = find(id);
        if (run.getStatus() != PayrollRunStatus.PENDING_APPROVAL) {
            throw new InvalidLifecycleTransitionException("Payroll run " + id + " cannot be approved from status " + run.getStatus() + ".");
        }
        assertNotSelfApproval(run, actor, correlationId);
        run.approve(actor, Instant.now());
        auditLog.runApproved(run.getId(), actor, correlationId);
        return toResponse(run);
    }

    @Transactional
    public PayrollRunDtos.Response reject(UUID id, String actor, UUID correlationId) {
        PayrollRun run = find(id);
        if (run.getStatus() != PayrollRunStatus.PENDING_APPROVAL) {
            throw new InvalidLifecycleTransitionException("Payroll run " + id + " cannot be rejected from status " + run.getStatus() + ".");
        }
        assertNotSelfApproval(run, actor, correlationId);
        run.reject(actor, Instant.now());
        auditLog.runRejected(run.getId(), actor, correlationId);
        return toResponse(run);
    }

    @Transactional
    public PayrollRunDtos.Response finalizeRun(UUID id, String actor, UUID correlationId) {
        PayrollRun run = find(id);
        if (run.getStatus() != PayrollRunStatus.APPROVED) {
            throw new InvalidLifecycleTransitionException("Payroll run " + id + " cannot be finalized from status " + run.getStatus() + ".");
        }
        assertNotSelfApproval(run, actor, correlationId);
        run.finalizeRun(actor, Instant.now());
        auditLog.runFinalized(run.getId(), actor, correlationId);

        outboxEventWriter.write("payroll.processed.v1", run.getId(), Map.of(
                "payrollRunId", run.getId().toString(),
                "periodId", run.getPeriodId().toString(),
                "employeeCount", run.getEmployeeSnapshot().size()), correlationId);

        return toResponse(run);
    }

    /**
     * Section J's rule, applied identically to approve/reject/finalize: the identity recorded as
     * {@code initiatedBy} may never act on its own run, even holding {@code payroll.approve}.
     */
    void assertNotSelfApproval(PayrollRun run, String actor, UUID correlationId) {
        if (run.getInitiatedBy() != null && run.getInitiatedBy().equals(actor)) {
            auditLog.selfApprovalRejected(run.getId(), actor, correlationId);
            throw new AccessDeniedException("The identity that initiated payroll run " + run.getId() + " may not approve, reject, or finalize it.");
        }
    }

    private PayrollRun find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run " + id + " was not found."));
    }

    private PayrollRunDtos.Response toResponse(PayrollRun run) {
        long lineCount = lineRepository.countByRunId(run.getId());
        long exceptionCount = exceptionRepository.countByRunId(run.getId());
        return new PayrollRunDtos.Response(run.getId(), run.getPeriodId(), run.getRunType(), run.getCorrectsRunId(),
                run.getStatus(), run.getEmployeeSnapshot().size(), (int) lineCount, (int) exceptionCount,
                run.getInitiatedBy(), run.getApprovedBy(), run.getApprovedAt(), run.getFinalizedAt(),
                run.getCreatedAt(), run.getUpdatedAt());
    }
}
