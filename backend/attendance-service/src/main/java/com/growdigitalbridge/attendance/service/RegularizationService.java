package com.growdigitalbridge.attendance.service;

import com.growdigitalbridge.attendance.api.dto.Decision;
import com.growdigitalbridge.attendance.api.dto.PageResponse;
import com.growdigitalbridge.attendance.api.dto.RegularizationDtos;
import com.growdigitalbridge.attendance.domain.AttendanceRecord;
import com.growdigitalbridge.attendance.domain.RegularizationRequest;
import com.growdigitalbridge.attendance.domain.RegularizationStatus;
import com.growdigitalbridge.attendance.repository.AttendanceRecordRepository;
import com.growdigitalbridge.attendance.repository.RegularizationRequestRepository;
import com.growdigitalbridge.attendance.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.attendance.service.exception.InvalidRequestException;
import com.growdigitalbridge.attendance.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns attendance regularization (correction) requests. Approval applies the corrected
 * check-in/check-out directly to the underlying {@link AttendanceRecord} - creating it first
 * if the employee never checked in - since that record is the "final attendance effect" this
 * request exists to correct (see docs/workflows/WORKFLOWS.md). Approval does not also finalize
 * the record: finalization remains the separate, explicit {@code /attendance/{id}/finalize}
 * action, since no documented rule ties regularization approval to finalization.
 */
@Service
public class RegularizationService {

    private final RegularizationRequestRepository repository;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceAccessGuard accessGuard;
    private final OrganizationScopeChecker scopeChecker;
    private final OutboxEventWriter outboxEventWriter;

    public RegularizationService(RegularizationRequestRepository repository, AttendanceRecordRepository recordRepository,
                                  AttendanceAccessGuard accessGuard, OrganizationScopeChecker scopeChecker,
                                  OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.recordRepository = recordRepository;
        this.accessGuard = accessGuard;
        this.scopeChecker = scopeChecker;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public RegularizationDtos.Response create(Authentication authentication, RegularizationDtos.CreateRequest request, String actor) {
        UUID employeeRef = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (request.requestedCheckInAt() == null && request.requestedCheckOutAt() == null) {
            throw new InvalidRequestException("At least one corrected check-in or check-out time must be provided.");
        }
        Instant now = Instant.now();
        RegularizationRequest regularization = new RegularizationRequest(UUID.randomUUID(), employeeRef, request.workDate(),
                request.requestedCheckInAt(), request.requestedCheckOutAt(), request.reason(), actor, now);
        repository.save(regularization);
        return toResponse(regularization);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegularizationDtos.Response> list(Authentication authentication, UUID employeeId, Pageable pageable) {
        AttendanceAccessGuard.ListScope scope = accessGuard.resolveListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing regularization requests requires self, team, or all read scope.");
        }
        Page<RegularizationRequest> page;
        if (scope.unrestricted()) {
            page = employeeId == null ? repository.findAll(pageable) : repository.searchWithinScope(Set.of(employeeId), pageable);
        } else if (scope.allowedIds().isEmpty() || (employeeId != null && !scope.allowedIds().contains(employeeId))) {
            page = Page.empty(pageable);
        } else {
            page = repository.searchWithinScope(employeeId != null ? Set.of(employeeId) : scope.allowedIds(), pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public RegularizationDtos.Response decide(UUID id, Authentication authentication, RegularizationDtos.DecisionRequest request,
                                               String actor, UUID correlationId) {
        RegularizationRequest regularization = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regularization request " + id + " was not found."));
        if (!scopeChecker.canDecide(authentication, regularization.getEmployeeRef(),
                "attendance.regularize.approve.team", "attendance.regularize.approve.all")) {
            throw new ResourceNotFoundException("Regularization request " + id + " was not found.");
        }
        if (regularization.getStatus() != RegularizationStatus.SUBMITTED) {
            throw new InvalidLifecycleTransitionException("Regularization request " + id + " has already been decided.");
        }

        Instant now = Instant.now();
        if (request.decision() == Decision.APPROVED) {
            AttendanceRecord record = recordRepository.findByEmployeeRefAndWorkDate(regularization.getEmployeeRef(), regularization.getWorkDate())
                    .orElseGet(() -> recordRepository.save(new AttendanceRecord(UUID.randomUUID(), regularization.getEmployeeRef(),
                            regularization.getWorkDate(), regularization.getRequestedCheckInAt(), actor, now)));
            record.applyRegularization(regularization.getRequestedCheckInAt(), regularization.getRequestedCheckOutAt(), actor, now);
            regularization.decide(RegularizationStatus.APPROVED, actor, now);
            outboxEventWriter.write("attendance.regularization-approved.v1", regularization.getId(), Map.of(
                    "requestId", regularization.getId().toString(),
                    "attendanceId", record.getId().toString(),
                    "employeeId", regularization.getEmployeeRef().toString()), correlationId);
        } else {
            regularization.decide(RegularizationStatus.REJECTED, actor, now);
        }
        return toResponse(regularization);
    }

    private RegularizationDtos.Response toResponse(RegularizationRequest request) {
        return new RegularizationDtos.Response(request.getId(), request.getEmployeeRef(), request.getWorkDate(),
                request.getRequestedCheckInAt(), request.getRequestedCheckOutAt(), request.getReason(),
                request.getStatus(), request.getDecidedBy(), request.getDecidedAt());
    }
}
