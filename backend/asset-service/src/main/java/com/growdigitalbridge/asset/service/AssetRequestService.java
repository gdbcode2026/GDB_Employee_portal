package com.growdigitalbridge.asset.service;

import com.growdigitalbridge.asset.api.dto.AssetRequestDtos;
import com.growdigitalbridge.asset.domain.AssetRequest;
import com.growdigitalbridge.asset.domain.AssetRequestStatus;
import com.growdigitalbridge.asset.repository.AssetRequestRepository;
import com.growdigitalbridge.asset.service.exception.InvalidRequestException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns AssetRequest submission and the application of its eventual approval outcome.
 *
 * Per the explicit instruction to integrate with Workflow "through the documented
 * WORKFLOW_COMPLETED contract only": this service never calls Workflow Service to start an
 * instance. A plain {@code asset.request.self} caller has no {@code workflow.manage}
 * authority, and WORKFLOWS.md documents no approver-resolution policy Asset could supply on
 * its behalf. Instead, {@code create} simply persists the request as SUBMITTED; an
 * administrator with {@code workflow.manage} starts a Workflow instance against it
 * out-of-band, referencing this request's ID as {@code subjectRef}. {@link
 * com.growdigitalbridge.asset.messaging.AssetEventListener} applies the terminal outcome when
 * {@code workflow.completed.v1} eventually arrives.
 */
@Service
public class AssetRequestService {

    private final AssetRequestRepository repository;
    private final AssetAccessGuard accessGuard;

    public AssetRequestService(AssetRequestRepository repository, AssetAccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public AssetRequestDtos.Response create(AssetRequestDtos.CreateRequest request, Authentication authentication, String actor) {
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new AccessDeniedException("Could not resolve the caller's own employee reference."));
        AssetRequest assetRequest = new AssetRequest(UUID.randomUUID(), self, request.type(), request.justification(), actor, Instant.now());
        repository.save(assetRequest);
        return toResponse(assetRequest);
    }

    /**
     * Applies a terminal {@code workflow.completed.v1} outcome. Idempotent both via the
     * caller's inbox check and this "only while still SUBMITTED" state guard, so a duplicate
     * delivery of the same event is a safe no-op.
     */
    @Transactional
    public void applyWorkflowOutcome(UUID assetRequestId, UUID workflowId, String outcome, String actor, Instant now) {
        repository.findById(assetRequestId).ifPresent(assetRequest -> {
            if (assetRequest.getStatus() != AssetRequestStatus.SUBMITTED) {
                return;
            }
            AssetRequestStatus status = mapOutcome(outcome);
            assetRequest.applyWorkflowOutcome(status, workflowId, actor, now);
        });
    }

    private AssetRequestStatus mapOutcome(String outcome) {
        try {
            return AssetRequestStatus.valueOf(outcome);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unrecognized workflow outcome: " + outcome);
        }
    }

    private AssetRequestDtos.Response toResponse(AssetRequest assetRequest) {
        return new AssetRequestDtos.Response(assetRequest.getId(), assetRequest.getEmployeeRef(), assetRequest.getType(),
                assetRequest.getJustification(), assetRequest.getStatus(), assetRequest.getWorkflowRef(),
                assetRequest.getCreatedAt(), assetRequest.getUpdatedAt());
    }
}
