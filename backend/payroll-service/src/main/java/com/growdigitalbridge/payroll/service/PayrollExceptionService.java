package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.PayrollExceptionDtos;
import com.growdigitalbridge.payroll.domain.PayrollException;
import com.growdigitalbridge.payroll.domain.PayrollExceptionStatus;
import com.growdigitalbridge.payroll.repository.PayrollExceptionRepository;
import com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/resolution access to {@link PayrollException} (item 4). Resolving an exception is a
 * record-keeping action only - it never retroactively recalculates the run or its lines, and it
 * never decides whether the exception *should* have blocked the run (that policy remains
 * PENDING_GDB_APPROVAL, Section X).
 */
@Service
public class PayrollExceptionService {

    private final PayrollExceptionRepository repository;
    private final PayrollAuditLog auditLog;

    public PayrollExceptionService(PayrollExceptionRepository repository, PayrollAuditLog auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayrollExceptionDtos.Response> list(UUID runId, Pageable pageable) {
        var page = runId == null ? repository.findAllByOrderByDetectedAtDesc(pageable) : repository.findByRunId(runId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public PayrollExceptionDtos.Response resolve(UUID id, String actor, UUID correlationId) {
        PayrollException exception = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll exception " + id + " was not found."));
        if (exception.getStatus() == PayrollExceptionStatus.RESOLVED) {
            throw new InvalidLifecycleTransitionException("Payroll exception " + id + " is already resolved.");
        }
        exception.resolve(actor, Instant.now());
        repository.save(exception);
        auditLog.payrollExceptionResolved(exception.getId(), exception.getRunId(), exception.getEmployeeRef(), actor, correlationId);
        return toResponse(exception);
    }

    private PayrollExceptionDtos.Response toResponse(PayrollException exception) {
        return new PayrollExceptionDtos.Response(exception.getId(), exception.getRunId(), exception.getEmployeeRef(),
                exception.getReason(), exception.getStatus(), exception.getDetectedAt(), exception.getResolvedAt(), exception.getResolvedBy());
    }
}
