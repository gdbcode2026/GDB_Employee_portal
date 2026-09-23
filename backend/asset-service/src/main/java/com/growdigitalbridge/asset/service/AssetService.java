package com.growdigitalbridge.asset.service;

import com.growdigitalbridge.asset.api.dto.AssetAssignmentDtos;
import com.growdigitalbridge.asset.api.dto.AssetDtos;
import com.growdigitalbridge.asset.api.dto.PageResponse;
import com.growdigitalbridge.asset.domain.Asset;
import com.growdigitalbridge.asset.domain.AssetAssignment;
import com.growdigitalbridge.asset.domain.AssetStatus;
import com.growdigitalbridge.asset.repository.AssetAssignmentRepository;
import com.growdigitalbridge.asset.repository.AssetRepository;
import com.growdigitalbridge.asset.service.exception.ConflictException;
import com.growdigitalbridge.asset.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.asset.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns asset inventory, assignment, and return. {@code RETIRED} is a documented state with no
 * reachable transition in this increment - see the V1 migration comment for why.
 */
@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetAssignmentRepository assignmentRepository;
    private final AssetAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public AssetService(AssetRepository assetRepository, AssetAssignmentRepository assignmentRepository,
                         AssetAccessGuard accessGuard, OutboxEventWriter outboxEventWriter) {
        this.assetRepository = assetRepository;
        this.assignmentRepository = assignmentRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public AssetDtos.Response create(AssetDtos.CreateRequest request, String actor) {
        if (assetRepository.existsByTag(request.tag())) {
            throw new ConflictException("An asset with tag " + request.tag() + " already exists.");
        }
        Asset asset = new Asset(UUID.randomUUID(), request.tag(), request.type(), request.serial(), actor, Instant.now());
        assetRepository.save(asset);
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetDtos.Response> list(Pageable pageable) {
        Page<Asset> page = assetRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<AssetDtos.Response> listMine(Authentication authentication) {
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new AccessDeniedException("Could not resolve the caller's own employee reference."));
        List<AssetAssignment> assignments = assignmentRepository.findByEmployeeRefAndReturnedAtIsNull(self);
        List<UUID> assetIds = assignments.stream().map(AssetAssignment::getAssetId).toList();
        return assetRepository.findAllById(assetIds).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssetAssignmentDtos.Response assign(UUID assetId, AssetAssignmentDtos.CreateRequest request, String actor,
                                                UUID correlationId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset " + assetId + " was not found."));
        if (asset.getStatus() != AssetStatus.AVAILABLE) {
            throw new InvalidLifecycleTransitionException("Asset " + assetId + " is not available for assignment.");
        }
        Instant now = Instant.now();
        AssetAssignment assignment = new AssetAssignment(UUID.randomUUID(), assetId, request.employeeRef(), now, actor, now);
        assignmentRepository.save(assignment);
        asset.markAssigned(actor, now);

        outboxEventWriter.write("asset.assigned.v1", asset.getId(), Map.of(
                "assetId", asset.getId().toString(),
                "assignmentId", assignment.getId().toString(),
                "employeeRef", assignment.getEmployeeRef().toString()), correlationId);

        return toResponse(assignment);
    }

    @Transactional
    public AssetAssignmentDtos.Response returnAsset(UUID assignmentId, AssetAssignmentDtos.ReturnRequest request, String actor) {
        AssetAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset assignment " + assignmentId + " was not found."));
        if (!assignment.isOpen()) {
            throw new InvalidLifecycleTransitionException("Asset assignment " + assignmentId + " has already been returned.");
        }
        Instant now = Instant.now();
        assignment.recordReturn(now, request.conditionNotes(), actor, now);
        Asset asset = assetRepository.findById(assignment.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset " + assignment.getAssetId() + " was not found."));
        asset.markAvailable(actor, now);
        return toResponse(assignment);
    }

    private AssetDtos.Response toResponse(Asset asset) {
        return new AssetDtos.Response(asset.getId(), asset.getTag(), asset.getType(), asset.getSerial(), asset.getStatus(),
                asset.getCreatedAt(), asset.getUpdatedAt());
    }

    private AssetAssignmentDtos.Response toResponse(AssetAssignment assignment) {
        return new AssetAssignmentDtos.Response(assignment.getId(), assignment.getAssetId(), assignment.getEmployeeRef(),
                assignment.getAssignedAt(), assignment.getReturnedAt(), assignment.getConditionNotes(),
                assignment.getCreatedAt(), assignment.getUpdatedAt());
    }
}
