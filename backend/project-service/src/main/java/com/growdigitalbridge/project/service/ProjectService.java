package com.growdigitalbridge.project.service;

import com.growdigitalbridge.project.api.dto.PageResponse;
import com.growdigitalbridge.project.api.dto.ProjectDtos;
import com.growdigitalbridge.project.domain.MembershipRole;
import com.growdigitalbridge.project.domain.Project;
import com.growdigitalbridge.project.domain.ProjectMembership;
import com.growdigitalbridge.project.domain.ProjectStatus;
import com.growdigitalbridge.project.repository.ProjectMembershipRepository;
import com.growdigitalbridge.project.repository.ProjectRepository;
import com.growdigitalbridge.project.service.exception.ConflictException;
import com.growdigitalbridge.project.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final ProjectMembershipRepository membershipRepository;
    private final ProjectAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public ProjectService(ProjectRepository repository, ProjectMembershipRepository membershipRepository,
                           ProjectAccessGuard accessGuard, OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.membershipRepository = membershipRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public ProjectDtos.Response create(Authentication authentication, ProjectDtos.CreateRequest request, String actor, UUID correlationId) {
        UUID ownerRef = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (repository.existsByCode(request.code())) {
            throw new ConflictException("Project code '" + request.code() + "' already exists.");
        }
        Instant now = Instant.now();
        Project project = new Project(UUID.randomUUID(), request.code(), request.name(), ownerRef, actor, now);
        repository.save(project);

        // The creator is automatically an active LEAD member, so project-read visibility
        // (which is membership-scoped) never excludes the person who just created it.
        membershipRepository.save(new ProjectMembership(UUID.randomUUID(), project.getId(), ownerRef, MembershipRole.LEAD, actor, now));

        outboxEventWriter.write("project.created.v1", project.getId(), Map.of(
                "projectId", project.getId().toString(),
                "code", project.getCode(),
                "ownerId", ownerRef.toString(),
                "status", project.getStatus().name()), correlationId);

        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectDtos.Response getById(UUID id, Authentication authentication) {
        Project project = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " was not found."));
        if (!accessGuard.canViewProject(authentication, id)) {
            throw new ResourceNotFoundException("Project " + id + " was not found.");
        }
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectDtos.Response> list(Authentication authentication, ProjectStatus status, String query, Pageable pageable) {
        ProjectAccessGuard.ProjectListScope scope = accessGuard.resolveProjectListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing projects requires project read or manage authority.");
        }
        if (!scope.unrestricted() && scope.allowedProjectIds().isEmpty()) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        Page<Project> page = scope.unrestricted()
                ? repository.searchAll(status, query, pageable)
                : repository.searchWithinScope(scope.allowedProjectIds(), status, query, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public ProjectDtos.Response update(UUID id, ProjectDtos.UpdateRequest request, String actor, UUID correlationId) {
        Project project = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " was not found."));
        List<String> changed = new ArrayList<>();
        Instant now = Instant.now();

        if (request.name() != null && !request.name().equals(project.getName())) {
            project.updateDetails(request.name(), actor, now);
            changed.add("name");
        }
        if (request.status() != null && request.status() != project.getStatus()) {
            project.changeStatus(request.status(), actor, now);
            changed.add("status");
        }

        if (!changed.isEmpty()) {
            outboxEventWriter.write("project.updated.v1", project.getId(),
                    Map.of("projectId", project.getId().toString(), "changedFields", changed), correlationId);
        }
        return toResponse(project);
    }

    private ProjectDtos.Response toResponse(Project project) {
        return new ProjectDtos.Response(project.getId(), project.getCode(), project.getName(), project.getOwnerRef(),
                project.getStatus(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
