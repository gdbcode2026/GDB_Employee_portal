package com.growdigitalbridge.organization.service;

import com.growdigitalbridge.organization.api.dto.PageResponse;
import com.growdigitalbridge.organization.api.dto.ReportingRelationDtos;
import com.growdigitalbridge.organization.domain.ReportingRelation;
import com.growdigitalbridge.organization.domain.ReportingRelationStatus;
import com.growdigitalbridge.organization.repository.ReportingRelationRepository;
import com.growdigitalbridge.organization.service.exception.ConflictException;
import com.growdigitalbridge.organization.service.exception.InvalidReportingRelationException;
import com.growdigitalbridge.organization.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the reporting graph that is the sole, authoritative source for manager/team-scope
 * authorization elsewhere in the platform. No method here accepts a client-supplied team
 * identifier; scope is always derived by walking {@link ReportingRelation} data.
 */
@Service
public class ReportingRelationService {

    private static final int MAX_HIERARCHY_DEPTH = 1000;

    private final ReportingRelationRepository repository;

    public ReportingRelationService(ReportingRelationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportingRelationDtos.Response> list(UUID employeeRef, UUID managerEmployeeRef,
                                                               ReportingRelationStatus status, Pageable pageable) {
        return PageResponse.of(repository.search(employeeRef, managerEmployeeRef, status, pageable).map(this::toResponse));
    }

    @Transactional
    public ReportingRelationDtos.Response create(ReportingRelationDtos.CreateRequest request, String actor) {
        if (request.employeeRef().equals(request.managerEmployeeRef())) {
            throw new InvalidReportingRelationException("An employee cannot be their own manager.");
        }
        if (repository.existsByEmployeeRefAndStatus(request.employeeRef(), ReportingRelationStatus.ACTIVE)) {
            throw new ConflictException("Employee " + request.employeeRef()
                    + " already has an active reporting relation; end it before assigning a new one.");
        }
        assertNoCycle(request.employeeRef(), request.managerEmployeeRef());
        ReportingRelation relation = new ReportingRelation(UUID.randomUUID(), request.employeeRef(),
                request.managerEmployeeRef(), request.effectiveStartDate(), actor, Instant.now());
        return toResponse(repository.save(relation));
    }

    @Transactional
    public ReportingRelationDtos.Response end(UUID id, ReportingRelationDtos.EndRequest request, String actor) {
        ReportingRelation relation = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reporting relation " + id + " was not found."));
        if (relation.getStatus() != ReportingRelationStatus.ACTIVE) {
            throw new ConflictException("Reporting relation " + id + " is already ended.");
        }
        if (request.effectiveEndDate().isBefore(relation.getEffectiveStartDate())) {
            throw new InvalidReportingRelationException("The end date cannot precede the relation's start date.");
        }
        relation.end(request.effectiveEndDate(), actor, Instant.now());
        return toResponse(relation);
    }

    @Transactional(readOnly = true)
    public ReportingRelationDtos.ScopeResponse resolveScope(UUID managerEmployeeRef) {
        return new ReportingRelationDtos.ScopeResponse(managerEmployeeRef, repository.findReportingScope(managerEmployeeRef));
    }

    private void assertNoCycle(UUID employeeRef, UUID managerEmployeeRef) {
        UUID current = managerEmployeeRef;
        int depth = 0;
        while (current != null) {
            if (current.equals(employeeRef)) {
                throw new InvalidReportingRelationException("Assigning this manager would create a reporting-hierarchy cycle.");
            }
            if (++depth > MAX_HIERARCHY_DEPTH) {
                throw new InvalidReportingRelationException("Reporting hierarchy exceeds the maximum supported depth.");
            }
            current = repository.findByEmployeeRefAndStatus(current, ReportingRelationStatus.ACTIVE)
                    .map(ReportingRelation::getManagerEmployeeRef)
                    .orElse(null);
        }
    }

    private ReportingRelationDtos.Response toResponse(ReportingRelation relation) {
        return new ReportingRelationDtos.Response(relation.getId(), relation.getEmployeeRef(), relation.getManagerEmployeeRef(),
                relation.getEffectiveStartDate(), relation.getEffectiveEndDate(), relation.getStatus());
    }
}
