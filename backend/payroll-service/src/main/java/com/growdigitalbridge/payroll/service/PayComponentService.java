package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.PayComponentDtos;
import com.growdigitalbridge.payroll.domain.PayComponent;
import com.growdigitalbridge.payroll.repository.PayComponentRepository;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the {@link PayComponent} catalogue's *structure* only (item 2): code, name, type,
 * active status. No rate, amount, threshold, or eligibility value is ever accepted here - GDB's
 * actual catalogue content is PENDING_GDB_APPROVAL (Section X). {@code code} is immutable once
 * created (duplicate-code protection via a database unique constraint, surfaced here as a clear
 * {@code 409}); {@code type} is likewise immutable once created to avoid silently reclassifying a
 * component already referenced by compensation records.
 */
@Service
public class PayComponentService {

    private final PayComponentRepository repository;
    private final PayrollAuditLog auditLog;

    public PayComponentService(PayComponentRepository repository, PayrollAuditLog auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Transactional
    public PayComponentDtos.Response create(PayComponentDtos.CreateRequest request, String actor, UUID correlationId) {
        if (repository.existsByCode(request.code())) {
            throw new ConflictException("A pay component with code '" + request.code() + "' already exists.");
        }
        Instant now = Instant.now();
        PayComponent component = new PayComponent(UUID.randomUUID(), request.code(), request.name(), request.type(), true, actor, now);
        repository.save(component);
        auditLog.payComponentCreated(component.getId(), actor, correlationId);
        return toResponse(component);
    }

    @Transactional(readOnly = true)
    public PayComponentDtos.Response getById(UUID id, String actor, UUID correlationId) {
        PayComponent component = find(id);
        auditLog.sensitiveRead("pay_component", id, actor, correlationId);
        return toResponse(component);
    }

    @Transactional(readOnly = true)
    public PageResponse<PayComponentDtos.Response> list(Pageable pageable) {
        return PageResponse.of(repository.findAllByOrderByCodeAsc(pageable).map(this::toResponse));
    }

    @Transactional
    public PayComponentDtos.Response update(UUID id, PayComponentDtos.UpdateRequest request, String actor, UUID correlationId) {
        PayComponent component = find(id);
        component.update(request.name(), request.active(), actor, Instant.now());
        repository.save(component);
        auditLog.payComponentUpdated(id, actor, correlationId);
        return toResponse(component);
    }

    private PayComponent find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pay component " + id + " was not found."));
    }

    private PayComponentDtos.Response toResponse(PayComponent component) {
        return new PayComponentDtos.Response(component.getId(), component.getCode(), component.getName(), component.getType(),
                component.isActive(), component.getCreatedAt(), component.getUpdatedAt());
    }
}
