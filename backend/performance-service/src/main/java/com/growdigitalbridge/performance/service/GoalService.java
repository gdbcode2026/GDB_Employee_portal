package com.growdigitalbridge.performance.service;

import com.growdigitalbridge.performance.api.dto.GoalDtos;
import com.growdigitalbridge.performance.api.dto.PageResponse;
import com.growdigitalbridge.performance.domain.Goal;
import com.growdigitalbridge.performance.domain.GoalStatus;
import com.growdigitalbridge.performance.repository.GoalRepository;
import com.growdigitalbridge.performance.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Owns per-employee goals. Mutation is deliberately self-only: RBAC.md documents no team/all goal-manage permission. */
@Service
public class GoalService {

    private final GoalRepository repository;
    private final PerformanceAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public GoalService(GoalRepository repository, PerformanceAccessGuard accessGuard, OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public GoalDtos.Response create(Authentication authentication, GoalDtos.CreateRequest request, String actor, UUID correlationId) {
        UUID employeeRef = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        Instant now = Instant.now();
        Goal goal = new Goal(UUID.randomUUID(), employeeRef, request.title(), request.description(), request.target(), actor, now);
        repository.save(goal);

        outboxEventWriter.write("goal.created.v1", goal.getId(), Map.of(
                "goalId", goal.getId().toString(), "employeeId", employeeRef.toString(), "status", goal.getStatus().name()), correlationId);

        return toResponse(goal);
    }

    @Transactional(readOnly = true)
    public PageResponse<GoalDtos.Response> list(Authentication authentication, GoalStatus status, Pageable pageable) {
        PerformanceAccessGuard.ReadScope scope = accessGuard.resolveGoalReadScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing goals requires self, team, or all read scope.");
        }
        if (!scope.unrestricted() && scope.employeeRefs().isEmpty()) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        Page<Goal> page = scope.unrestricted()
                ? repository.searchAll(status, pageable)
                : repository.searchWithinScope(scope.employeeRefs(), status, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public GoalDtos.Response update(Authentication authentication, UUID id, GoalDtos.UpdateRequest request, String actor, UUID correlationId) {
        Goal goal = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal " + id + " was not found."));
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (!goal.getEmployeeRef().equals(self)) {
            throw new ResourceNotFoundException("Goal " + id + " was not found.");
        }

        Instant now = Instant.now();
        boolean detailsChanged = request.title() != null || request.description() != null || request.target() != null;
        if (detailsChanged) {
            goal.updateDetails(request.title() != null ? request.title() : goal.getTitle(),
                    request.description() != null ? request.description() : goal.getDescription(),
                    request.target() != null ? request.target() : goal.getTarget(), actor, now);
        }
        boolean statusChanged = request.status() != null && request.status() != goal.getStatus();
        if (statusChanged) {
            goal.changeStatus(request.status(), actor, now);
            outboxEventWriter.write("goal.status-changed.v1", goal.getId(), Map.of(
                    "goalId", goal.getId().toString(), "employeeId", self.toString(), "status", goal.getStatus().name()), correlationId);
        }
        return toResponse(goal);
    }

    private GoalDtos.Response toResponse(Goal goal) {
        return new GoalDtos.Response(goal.getId(), goal.getEmployeeRef(), goal.getTitle(), goal.getDescription(),
                goal.getTarget(), goal.getStatus(), goal.getCreatedAt(), goal.getUpdatedAt());
    }
}
