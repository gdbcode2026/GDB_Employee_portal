package com.growdigitalbridge.project.service;

import com.growdigitalbridge.project.api.dto.ProjectMembershipDtos;
import com.growdigitalbridge.project.domain.MembershipRole;
import com.growdigitalbridge.project.domain.MembershipStatus;
import com.growdigitalbridge.project.domain.ProjectMembership;
import com.growdigitalbridge.project.repository.ProjectMembershipRepository;
import com.growdigitalbridge.project.repository.ProjectRepository;
import com.growdigitalbridge.project.service.exception.ConflictException;
import com.growdigitalbridge.project.service.exception.InvalidRequestException;
import com.growdigitalbridge.project.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMembershipService {

    private final ProjectMembershipRepository repository;
    private final ProjectRepository projectRepository;
    private final OutboxEventWriter outboxEventWriter;

    public ProjectMembershipService(ProjectMembershipRepository repository, ProjectRepository projectRepository,
                                     OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional(readOnly = true)
    public List<ProjectMembershipDtos.Response> list(UUID projectId) {
        requireProject(projectId);
        return repository.findByProjectId(projectId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectMembershipDtos.Response add(UUID projectId, ProjectMembershipDtos.AddRequest request, String actor, UUID correlationId) {
        requireProject(projectId);
        MembershipRole role = request.role() != null ? request.role() : MembershipRole.MEMBER;
        Instant now = Instant.now();

        ProjectMembership membership = repository.findByProjectIdAndEmployeeRef(projectId, request.employeeRef()).orElse(null);
        if (membership != null && membership.getStatus() == MembershipStatus.ACTIVE) {
            throw new ConflictException("Employee " + request.employeeRef() + " is already an active member of this project.");
        }
        if (membership != null) {
            membership.reactivate(role, actor, now);
        } else {
            membership = new ProjectMembership(UUID.randomUUID(), projectId, request.employeeRef(), role, actor, now);
            repository.save(membership);
        }

        outboxEventWriter.write("project.member-added.v1", projectId, Map.of(
                "projectId", projectId.toString(),
                "employeeId", request.employeeRef().toString(),
                "role", role.name()), correlationId);

        return toResponse(membership);
    }

    @Transactional
    public ProjectMembershipDtos.Response update(UUID projectId, UUID memberId, ProjectMembershipDtos.UpdateRequest request,
                                                  String actor, UUID correlationId) {
        requireProject(projectId);
        ProjectMembership membership = repository.findById(memberId)
                .filter(candidate -> candidate.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Project member " + memberId + " was not found."));
        if (request.role() == null && request.status() == null) {
            throw new InvalidRequestException("Either role or status must be provided.");
        }

        Instant now = Instant.now();
        if (request.status() == MembershipStatus.REMOVED && membership.getStatus() == MembershipStatus.ACTIVE) {
            membership.remove(actor, now);
            outboxEventWriter.write("project.member-removed.v1", projectId, Map.of(
                    "projectId", projectId.toString(), "employeeId", membership.getEmployeeRef().toString()), correlationId);
        } else if (request.status() == MembershipStatus.ACTIVE && membership.getStatus() == MembershipStatus.REMOVED) {
            MembershipRole role = request.role() != null ? request.role() : membership.getRole();
            membership.reactivate(role, actor, now);
            outboxEventWriter.write("project.member-added.v1", projectId, Map.of(
                    "projectId", projectId.toString(), "employeeId", membership.getEmployeeRef().toString(), "role", role.name()), correlationId);
        } else if (request.role() != null) {
            membership.changeRole(request.role(), actor, now);
        }

        return toResponse(membership);
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project " + projectId + " was not found.");
        }
    }

    private ProjectMembershipDtos.Response toResponse(ProjectMembership membership) {
        return new ProjectMembershipDtos.Response(membership.getId(), membership.getProjectId(), membership.getEmployeeRef(),
                membership.getRole(), membership.getStatus(), membership.getCreatedAt(), membership.getUpdatedAt());
    }
}
