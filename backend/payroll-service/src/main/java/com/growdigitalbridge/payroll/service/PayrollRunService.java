package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.PayrollPeriodStatus;
import com.growdigitalbridge.payroll.domain.PayrollRun;
import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.domain.PayrollRunType;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunRepository;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.payroll.service.exception.InvalidRequestException;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@link PayrollRun} creation and the Section D state machine's foundation-level
 * transitions. No calculation happens anywhere in this class - {@code process} advances DRAFT/
 * REJECTED straight to CALCULATED without producing a single {@code PayrollRunLine} (there is no
 * such entity yet; that is Phase 2, blocked on the pending pay-component catalogue). {@code
 * submitForApproval} then advances CALCULATED to PENDING_APPROVAL, so the full documented graph
 * (DRAFT -&gt; CALCULATED -&gt; PENDING_APPROVAL -&gt; APPROVED -&gt; FINALIZED, with PENDING_APPROVAL -&gt;
 * REJECTED re-entering processing) is faithfully implemented even though no endpoint in Section
 * O names "process"/"submit" explicitly - both are minimum additions required to make decision 7
 * reachable at all, exactly like Section O's own flagged "minimum necessary additions".
 *
 * <p>Self-approval prevention (Section J): approve/reject/finalize all reject an attempt where
 * the acting identity equals the run's own {@code initiatedBy}, regardless of whether that
 * identity also holds {@code payroll.approve} - applied symmetrically to reject too, since
 * allowing a self-reject would let the same person route around the maker/checker split by a
 * different outcome.
 *
 * <p>Only {@code finalizeRun} produces a domain event: {@code payroll.processed.v1}
 * (PAYROLL_PROCESSED), with the exact payload Section Q already locks - run/period ID and
 * employee count, both known from the run's own snapshot without any calculation. {@code
 * payslip.generated.v1} is never produced here; it requires the {@code Payslip} entity, which
 * does not exist until Phase 4.
 */
@Service
public class PayrollRunService {

    private final PayrollRunRepository repository;
    private final PayrollPeriodRepository periodRepository;
    private final EmployeeClient employeeClient;
    private final OutboxEventWriter outboxEventWriter;
    private final PayrollAuditLog auditLog;

    public PayrollRunService(PayrollRunRepository repository, PayrollPeriodRepository periodRepository,
                              EmployeeClient employeeClient, OutboxEventWriter outboxEventWriter, PayrollAuditLog auditLog) {
        this.repository = repository;
        this.periodRepository = periodRepository;
        this.employeeClient = employeeClient;
        this.outboxEventWriter = outboxEventWriter;
        this.auditLog = auditLog;
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

    @Transactional
    public PayrollRunDtos.Response process(UUID id, String actor, UUID correlationId) {
        PayrollRun run = find(id);
        if (run.getStatus() != PayrollRunStatus.DRAFT && run.getStatus() != PayrollRunStatus.REJECTED) {
            throw new InvalidLifecycleTransitionException("Payroll run " + id + " cannot be processed from status " + run.getStatus() + ".");
        }
        run.process(actor, Instant.now());
        auditLog.runProcessed(run.getId(), actor, correlationId);
        return toResponse(run);
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
        return new PayrollRunDtos.Response(run.getId(), run.getPeriodId(), run.getRunType(), run.getCorrectsRunId(),
                run.getStatus(), run.getEmployeeSnapshot().size(), run.getInitiatedBy(), run.getApprovedBy(),
                run.getApprovedAt(), run.getFinalizedAt(), run.getCreatedAt(), run.getUpdatedAt());
    }
}
