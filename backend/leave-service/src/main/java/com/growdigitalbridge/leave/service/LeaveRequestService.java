package com.growdigitalbridge.leave.service;

import com.growdigitalbridge.leave.api.dto.Decision;
import com.growdigitalbridge.leave.api.dto.LeaveRequestDtos;
import com.growdigitalbridge.leave.api.dto.PageResponse;
import com.growdigitalbridge.leave.domain.LeaveBalance;
import com.growdigitalbridge.leave.domain.LeaveRequest;
import com.growdigitalbridge.leave.domain.LeaveRequestStatus;
import com.growdigitalbridge.leave.repository.LeaveBalanceRepository;
import com.growdigitalbridge.leave.repository.LeaveRequestRepository;
import com.growdigitalbridge.leave.repository.LeaveTypeRepository;
import com.growdigitalbridge.leave.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.leave.service.exception.InvalidRequestException;
import com.growdigitalbridge.leave.service.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Owns leave application, decisions, and cancellation. Units are the inclusive calendar-day
 * count between start and end date - weekend/holiday exclusion is not applied because no
 * holiday calendar or half-day policy is documented anywhere in this platform yet; this is a
 * flagged minimum decision, not an invented business rule. Balance reservation uses a
 * pessimistic row lock (see LeaveBalanceRepository) so concurrent requests against the same
 * balance cannot both succeed past the available amount.
 */
@Service
public class LeaveRequestService {

    private final LeaveRequestRepository repository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public LeaveRequestService(LeaveRequestRepository repository, LeaveBalanceRepository balanceRepository,
                                LeaveTypeRepository leaveTypeRepository, LeaveAccessGuard accessGuard,
                                OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.balanceRepository = balanceRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public LeaveRequestDtos.Response create(Authentication authentication, LeaveRequestDtos.CreateRequest request,
                                             String actor, UUID correlationId) {
        UUID employeeRef = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidRequestException("The end date cannot precede the start date.");
        }
        if (request.startDate().getYear() != request.endDate().getYear()) {
            throw new InvalidRequestException("Leave requests must fall within a single calendar year.");
        }
        if (!leaveTypeRepository.existsById(request.leaveTypeId())) {
            throw new ResourceNotFoundException("Leave type " + request.leaveTypeId() + " was not found.");
        }
        BigDecimal units = BigDecimal.valueOf(ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1);

        LeaveBalance balance = balanceRepository.findForUpdate(employeeRef, request.leaveTypeId(), request.startDate().getYear())
                .orElseThrow(() -> new InvalidRequestException("No leave balance has been allocated for this employee, type, and year."));
        if (balance.available().compareTo(units) < 0) {
            throw new InvalidRequestException("Insufficient leave balance: " + balance.available() + " day(s) available, " + units + " requested.");
        }

        Instant now = Instant.now();
        balance.reserve(units, actor, now);

        LeaveRequest leaveRequest = new LeaveRequest(UUID.randomUUID(), employeeRef, request.leaveTypeId(),
                request.startDate(), request.endDate(), units, request.reason(), actor, now);
        repository.save(leaveRequest);

        outboxEventWriter.write("leave.requested.v1", leaveRequest.getId(), Map.of(
                "requestId", leaveRequest.getId().toString(),
                "employeeId", employeeRef.toString(),
                "leaveTypeId", request.leaveTypeId().toString(),
                "startDate", request.startDate().toString(),
                "endDate", request.endDate().toString()), correlationId);

        return toResponse(leaveRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestDtos.Response> list(Authentication authentication, UUID employeeId, LeaveRequestStatus status,
                                                          UUID leaveTypeId, LocalDate from, LocalDate to, Pageable pageable) {
        LeaveAccessGuard.ListScope scope = accessGuard.resolveListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing leave requests requires self, team, or all read scope.");
        }
        Page<LeaveRequest> page;
        if (scope.unrestricted()) {
            page = employeeId == null
                    ? repository.searchAll(status, leaveTypeId, from, to, pageable)
                    : repository.searchWithinScope(Set.of(employeeId), status, leaveTypeId, from, to, pageable);
        } else if (scope.allowedIds().isEmpty() || (employeeId != null && !scope.allowedIds().contains(employeeId))) {
            page = Page.empty(pageable);
        } else {
            var effectiveIds = employeeId != null ? Set.of(employeeId) : scope.allowedIds();
            page = repository.searchWithinScope(effectiveIds, status, leaveTypeId, from, to, pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public LeaveRequestDtos.Response cancel(UUID id, Authentication authentication, String actor) {
        LeaveRequest leaveRequest = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request " + id + " was not found."));
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (!leaveRequest.getEmployeeRef().equals(self)) {
            throw new ResourceNotFoundException("Leave request " + id + " was not found.");
        }
        if (leaveRequest.getStatus() != LeaveRequestStatus.SUBMITTED) {
            throw new InvalidLifecycleTransitionException("Leave request " + id + " cannot be cancelled from its current state.");
        }
        Instant now = Instant.now();
        LeaveBalance balance = balanceRepository.findForUpdate(leaveRequest.getEmployeeRef(), leaveRequest.getLeaveTypeId(),
                        leaveRequest.getStartDate().getYear())
                .orElseThrow(() -> new IllegalStateException("Leave balance missing for a request that reserved it."));
        balance.release(leaveRequest.getUnits(), actor, now);
        leaveRequest.cancel(actor, now);
        return toResponse(leaveRequest);
    }

    @Transactional
    public LeaveRequestDtos.Response decide(UUID id, Authentication authentication, LeaveRequestDtos.DecisionRequest request,
                                             String actor, UUID correlationId) {
        LeaveRequest leaveRequest = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request " + id + " was not found."));
        if (!accessGuard.canActOnBehalfOf(authentication, leaveRequest.getEmployeeRef(), "leave.approve.team", "leave.approve.all")) {
            throw new ResourceNotFoundException("Leave request " + id + " was not found.");
        }
        if (leaveRequest.getStatus() != LeaveRequestStatus.SUBMITTED) {
            throw new InvalidLifecycleTransitionException("Leave request " + id + " has already been decided.");
        }
        Instant now = Instant.now();
        LeaveBalance balance = balanceRepository.findForUpdate(leaveRequest.getEmployeeRef(), leaveRequest.getLeaveTypeId(),
                        leaveRequest.getStartDate().getYear())
                .orElseThrow(() -> new IllegalStateException("Leave balance missing for a request that reserved it."));

        if (request.decision() == Decision.APPROVED) {
            balance.consume(leaveRequest.getUnits(), actor, now);
            leaveRequest.decide(LeaveRequestStatus.APPROVED, actor, now);
            outboxEventWriter.write("leave.approved.v1", leaveRequest.getId(), Map.of(
                    "requestId", leaveRequest.getId().toString(),
                    "employeeId", leaveRequest.getEmployeeRef().toString(),
                    "approvedUnits", leaveRequest.getUnits().toString()), correlationId);
        } else {
            balance.release(leaveRequest.getUnits(), actor, now);
            leaveRequest.decide(LeaveRequestStatus.REJECTED, actor, now);
            outboxEventWriter.write("leave.rejected.v1", leaveRequest.getId(), Map.of(
                    "requestId", leaveRequest.getId().toString(),
                    "employeeId", leaveRequest.getEmployeeRef().toString()), correlationId);
        }
        return toResponse(leaveRequest);
    }

    private LeaveRequestDtos.Response toResponse(LeaveRequest request) {
        return new LeaveRequestDtos.Response(request.getId(), request.getEmployeeRef(), request.getLeaveTypeId(),
                request.getStartDate(), request.getEndDate(), request.getUnits(), request.getReason(),
                request.getStatus(), request.getDecidedBy(), request.getDecidedAt());
    }
}
