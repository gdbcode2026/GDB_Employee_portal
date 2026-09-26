package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PayrollPeriodDtos;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.repository.PayrollPeriodRepository;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.InvalidRequestException;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@link PayrollPeriod} creation/retrieval (decision 3: monthly periods only).
 * {@code startDate}/{@code endDate} are always derived from {@code year}/{@code month}, never
 * accepted as input, so no additional payroll frequency can be created through this API.
 */
@Service
public class PayrollPeriodService {

    private final PayrollPeriodRepository repository;
    private final PayrollAuditLog auditLog;

    public PayrollPeriodService(PayrollPeriodRepository repository, PayrollAuditLog auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Transactional
    public PayrollPeriodDtos.Response create(PayrollPeriodDtos.CreateRequest request, String actor, UUID correlationId) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.of(request.year(), request.month());
        } catch (java.time.DateTimeException e) {
            throw new InvalidRequestException("Year/month is not a valid calendar month: " + e.getMessage());
        }
        if (repository.findByYearAndMonth(request.year(), request.month()).isPresent()) {
            throw new ConflictException("A payroll period for " + yearMonth + " already exists.");
        }
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        if (request.cutOffDate() != null && (request.cutOffDate().isBefore(startDate) || request.cutOffDate().isAfter(endDate))) {
            throw new InvalidRequestException("Cut-off date must fall within the period's start and end dates.");
        }

        Instant now = Instant.now();
        PayrollPeriod period = new PayrollPeriod(UUID.randomUUID(), request.year(), request.month(),
                startDate, endDate, request.cutOffDate(), actor, now);
        repository.save(period);
        auditLog.periodCreated(period.getId(), actor, correlationId);
        return toResponse(period);
    }

    @Transactional(readOnly = true)
    public PayrollPeriodDtos.Response getById(UUID id, String actor, UUID correlationId) {
        PayrollPeriod period = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll period " + id + " was not found."));
        auditLog.sensitiveRead("period", id, actor, correlationId);
        return toResponse(period);
    }

    @Transactional(readOnly = true)
    public List<PayrollPeriodDtos.Response> list() {
        return repository.findAllByOrderByYearDescMonthDesc().stream().map(this::toResponse).toList();
    }

    private PayrollPeriodDtos.Response toResponse(PayrollPeriod period) {
        return new PayrollPeriodDtos.Response(period.getId(), period.getYear(), period.getMonth(),
                period.getStartDate(), period.getEndDate(), period.getCutOffDate(), period.getStatus(),
                period.getCreatedAt(), period.getUpdatedAt());
    }
}
