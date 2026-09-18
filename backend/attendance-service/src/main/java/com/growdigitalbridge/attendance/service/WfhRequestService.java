package com.growdigitalbridge.attendance.service;

import com.growdigitalbridge.attendance.api.dto.Decision;
import com.growdigitalbridge.attendance.api.dto.PageResponse;
import com.growdigitalbridge.attendance.api.dto.WfhRequestDtos;
import com.growdigitalbridge.attendance.domain.WfhRequest;
import com.growdigitalbridge.attendance.domain.WfhRequestStatus;
import com.growdigitalbridge.attendance.repository.WfhRequestRepository;
import com.growdigitalbridge.attendance.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.attendance.service.exception.InvalidRequestException;
import com.growdigitalbridge.attendance.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns work-from-home requests. No approval endpoint is listed in docs/api/API.md for this
 * sub-resource, but the RBAC permission catalogue documents {@code wfh.approve.team/approve.all}
 * - so a direct decision endpoint is provided here, mirroring the shape of Leave's documented
 * {@code /leave/requests/{id}/decisions}, rather than left unusable. See ApiExceptionHandler
 * report notes for this flagged minimum decision.
 */
@Service
public class WfhRequestService {

    private final WfhRequestRepository repository;
    private final AttendanceAccessGuard accessGuard;
    private final OrganizationScopeChecker scopeChecker;

    public WfhRequestService(WfhRequestRepository repository, AttendanceAccessGuard accessGuard,
                              OrganizationScopeChecker scopeChecker) {
        this.repository = repository;
        this.accessGuard = accessGuard;
        this.scopeChecker = scopeChecker;
    }

    @Transactional
    public WfhRequestDtos.Response create(Authentication authentication, WfhRequestDtos.CreateRequest request, String actor) {
        UUID employeeRef = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidRequestException("The end date cannot precede the start date.");
        }
        Instant now = Instant.now();
        WfhRequest wfhRequest = new WfhRequest(UUID.randomUUID(), employeeRef, request.startDate(), request.endDate(),
                request.reason(), actor, now);
        repository.save(wfhRequest);
        return toResponse(wfhRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<WfhRequestDtos.Response> list(Authentication authentication, UUID employeeId, Pageable pageable) {
        AttendanceAccessGuard.ListScope scope = accessGuard.resolveListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing WFH requests requires self, team, or all read scope.");
        }
        Page<WfhRequest> page;
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
    public WfhRequestDtos.Response decide(UUID id, Authentication authentication, WfhRequestDtos.DecisionRequest request, String actor) {
        WfhRequest wfhRequest = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WFH request " + id + " was not found."));
        if (!scopeChecker.canDecide(authentication, wfhRequest.getEmployeeRef(), "wfh.approve.team", "wfh.approve.all")) {
            throw new ResourceNotFoundException("WFH request " + id + " was not found.");
        }
        if (wfhRequest.getStatus() != WfhRequestStatus.SUBMITTED) {
            throw new InvalidLifecycleTransitionException("WFH request " + id + " has already been decided.");
        }
        wfhRequest.decide(request.decision() == Decision.APPROVED ? WfhRequestStatus.APPROVED : WfhRequestStatus.REJECTED,
                actor, Instant.now());
        return toResponse(wfhRequest);
    }

    private WfhRequestDtos.Response toResponse(WfhRequest request) {
        return new WfhRequestDtos.Response(request.getId(), request.getEmployeeRef(), request.getStartDate(),
                request.getEndDate(), request.getReason(), request.getStatus(), request.getDecidedBy(), request.getDecidedAt());
    }
}
