package com.growdigitalbridge.performance.service;

import com.growdigitalbridge.performance.api.dto.PageResponse;
import com.growdigitalbridge.performance.api.dto.PerformanceReviewDtos;
import com.growdigitalbridge.performance.domain.PerformanceReview;
import com.growdigitalbridge.performance.domain.ReviewCycle;
import com.growdigitalbridge.performance.domain.ReviewCycleStatus;
import com.growdigitalbridge.performance.domain.ReviewStatus;
import com.growdigitalbridge.performance.repository.PerformanceReviewRepository;
import com.growdigitalbridge.performance.repository.ReviewCycleRepository;
import com.growdigitalbridge.performance.service.exception.ConflictException;
import com.growdigitalbridge.performance.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.performance.service.exception.InvalidRequestException;
import com.growdigitalbridge.performance.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns performance reviews. RBAC.md documents only {@code performance.review.submit.team} for
 * the reviewer side - there is no separate self-review permission - so self-review is
 * represented structurally (a review row where {@code reviewerRef} equals {@code employeeRef})
 * and authorized the same way as any other review: the caller must be the assigned reviewer,
 * or hold {@code performance.manage}. See PerformanceAccessGuard for the full rationale.
 */
@Service
public class PerformanceReviewService {

    private final PerformanceReviewRepository repository;
    private final ReviewCycleRepository cycleRepository;
    private final PerformanceAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public PerformanceReviewService(PerformanceReviewRepository repository, ReviewCycleRepository cycleRepository,
                                     PerformanceAccessGuard accessGuard, OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.cycleRepository = cycleRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public PerformanceReviewDtos.Response create(Authentication authentication, PerformanceReviewDtos.CreateRequest request,
                                                  String actor, UUID correlationId) {
        ReviewCycle cycle = cycleRepository.findById(request.cycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Review cycle " + request.cycleId() + " was not found."));
        if (cycle.getStatus() != ReviewCycleStatus.ACTIVE) {
            throw new InvalidRequestException("Review cycle " + request.cycleId() + " is not active.");
        }

        boolean isAdmin = accessGuard.canManageAll(authentication);
        if (!isAdmin) {
            UUID self = accessGuard.resolveSelf(authentication)
                    .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
            if (!self.equals(request.reviewerRef())) {
                throw new AccessDeniedException("You may only create a review where you are the assigned reviewer.");
            }
        }
        if (!accessGuard.canReview(authentication, request.employeeRef())) {
            throw new AccessDeniedException("Reviewing this employee is outside your permitted scope.");
        }
        if (repository.existsByCycleIdAndEmployeeRefAndReviewerRef(request.cycleId(), request.employeeRef(), request.reviewerRef())) {
            throw new ConflictException("A review for this employee, reviewer, and cycle already exists.");
        }

        Instant now = Instant.now();
        PerformanceReview review = new PerformanceReview(UUID.randomUUID(), request.cycleId(), request.employeeRef(),
                request.reviewerRef(), actor, now);
        repository.save(review);

        outboxEventWriter.write("review.created.v1", review.getId(), Map.of(
                "reviewId", review.getId().toString(),
                "cycleId", request.cycleId().toString(),
                "employeeId", request.employeeRef().toString(),
                "reviewerId", request.reviewerRef().toString()), correlationId);

        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<PerformanceReviewDtos.Response> list(Authentication authentication, UUID cycleId, Pageable pageable) {
        PerformanceAccessGuard.ReviewReadScope scope = accessGuard.resolveReviewReadScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing reviews requires self, team, or all read scope.");
        }
        Page<PerformanceReview> page = switch (scope.level()) {
            case ALL -> repository.searchAll(cycleId, pageable);
            case TEAM -> scope.teamIds().isEmpty() ? Page.empty(pageable) : repository.searchWithinScope(scope.teamIds(), cycleId, pageable);
            case SELF -> repository.searchForSelf(scope.self(), cycleId, pageable);
            case DENIED -> Page.empty(pageable);
        };
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public PerformanceReviewDtos.Response submit(Authentication authentication, UUID id, PerformanceReviewDtos.SubmitRequest request,
                                                  String actor, UUID correlationId) {
        PerformanceReview review = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review " + id + " was not found."));

        boolean isAssignedReviewer = accessGuard.resolveSelf(authentication).map(self -> self.equals(review.getReviewerRef())).orElse(false);
        if (!isAssignedReviewer && !accessGuard.canManageAll(authentication)) {
            throw new ResourceNotFoundException("Performance review " + id + " was not found.");
        }
        if (review.getStatus() != ReviewStatus.DRAFT) {
            throw new InvalidLifecycleTransitionException("Performance review " + id + " has already been submitted.");
        }

        review.submit(request.rating(), request.comments(), actor, Instant.now());

        outboxEventWriter.write("review.submitted.v1", review.getId(), Map.of(
                "reviewId", review.getId().toString(),
                "cycleId", review.getCycleId().toString(),
                "employeeId", review.getEmployeeRef().toString(),
                "reviewerId", review.getReviewerRef().toString()), correlationId);

        return toResponse(review);
    }

    private PerformanceReviewDtos.Response toResponse(PerformanceReview review) {
        return new PerformanceReviewDtos.Response(review.getId(), review.getCycleId(), review.getEmployeeRef(), review.getReviewerRef(),
                review.getRating(), review.getComments(), review.getStatus(), review.getSubmittedAt(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
