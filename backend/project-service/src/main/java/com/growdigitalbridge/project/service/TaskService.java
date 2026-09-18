package com.growdigitalbridge.project.service;

import com.growdigitalbridge.project.api.dto.PageResponse;
import com.growdigitalbridge.project.api.dto.TaskDtos;
import com.growdigitalbridge.project.domain.Task;
import com.growdigitalbridge.project.domain.TaskPriority;
import com.growdigitalbridge.project.domain.TaskStatus;
import com.growdigitalbridge.project.repository.ProjectRepository;
import com.growdigitalbridge.project.repository.TaskRepository;
import com.growdigitalbridge.project.service.exception.InvalidRequestException;
import com.growdigitalbridge.project.service.exception.ResourceNotFoundException;
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
 * Owns tasks within a project. Visibility/authorization follows API.md's "Task read/manage
 * self/team; ... validate membership and state": nested project listing additionally
 * requires the caller to be a project member (unless they hold project.manage), and any
 * assignee supplied on create/update must itself be an active member of the task's project.
 */
@Service
public class TaskService {

    private final TaskRepository repository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public TaskService(TaskRepository repository, ProjectRepository projectRepository,
                        ProjectAccessGuard accessGuard, OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public TaskDtos.Response create(Authentication authentication, UUID projectId, TaskDtos.CreateRequest request,
                                     String actor, UUID correlationId) {
        requireProject(projectId);
        if (!accessGuard.canManageProjects(authentication)) {
            UUID self = accessGuard.resolveSelf(authentication)
                    .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
            if (!accessGuard.isActiveMember(projectId, self)) {
                throw new ResourceNotFoundException("Project " + projectId + " was not found.");
            }
        }
        if (request.assigneeRef() != null && !accessGuard.isActiveMember(projectId, request.assigneeRef())) {
            throw new InvalidRequestException("Assignee " + request.assigneeRef() + " is not an active member of this project.");
        }

        Instant now = Instant.now();
        TaskPriority priority = request.priority() != null ? request.priority() : TaskPriority.MEDIUM;
        Task task = new Task(UUID.randomUUID(), projectId, request.assigneeRef(), request.title(), request.description(),
                priority, request.dueDate(), actor, now);
        repository.save(task);

        outboxEventWriter.write("task.created.v1", task.getId(), Map.of(
                "taskId", task.getId().toString(),
                "projectId", projectId.toString(),
                "assigneeId", request.assigneeRef() == null ? "" : request.assigneeRef().toString()), correlationId);

        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDtos.Response> listInProject(Authentication authentication, UUID projectId, TaskStatus status,
                                                           TaskPriority priority, UUID assigneeId, Pageable pageable) {
        requireProject(projectId);
        if (accessGuard.canManageProjects(authentication)) {
            return PageResponse.of(repository.searchInProject(projectId, status, priority, assigneeId, pageable).map(this::toResponse));
        }
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " was not found."));
        if (!accessGuard.isActiveMember(projectId, self)) {
            throw new ResourceNotFoundException("Project " + projectId + " was not found.");
        }
        ProjectAccessGuard.TaskScope scope = accessGuard.resolveTaskScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing tasks requires self or team task authority.");
        }
        if (scope.assigneeRefs().isEmpty() || (assigneeId != null && !scope.assigneeRefs().contains(assigneeId))) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        Set<UUID> effective = assigneeId != null ? Set.of(assigneeId) : scope.assigneeRefs();
        return PageResponse.of(repository.searchInProjectWithinScope(projectId, effective, status, priority, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDtos.Response> listSelf(Authentication authentication, TaskStatus status, TaskPriority priority, Pageable pageable) {
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        return PageResponse.of(repository.searchForAssignee(self, status, priority, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDtos.Response> list(Authentication authentication, TaskStatus status, TaskPriority priority,
                                                 UUID assigneeId, Pageable pageable) {
        ProjectAccessGuard.TaskScope scope = accessGuard.resolveTaskScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing tasks requires team task authority or project management.");
        }
        if (scope.unrestricted()) {
            Page<Task> page = assigneeId == null
                    ? repository.searchAll(status, priority, pageable)
                    : repository.searchWithinScope(Set.of(assigneeId), status, priority, pageable);
            return PageResponse.of(page.map(this::toResponse));
        }
        if (scope.assigneeRefs().isEmpty() || (assigneeId != null && !scope.assigneeRefs().contains(assigneeId))) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        Set<UUID> effective = assigneeId != null ? Set.of(assigneeId) : scope.assigneeRefs();
        return PageResponse.of(repository.searchWithinScope(effective, status, priority, pageable).map(this::toResponse));
    }

    @Transactional
    public TaskDtos.Response update(Authentication authentication, UUID id, TaskDtos.UpdateRequest request, String actor, UUID correlationId) {
        Task task = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + id + " was not found."));
        if (!accessGuard.canAccessTask(authentication, task.getAssigneeRef())) {
            throw new ResourceNotFoundException("Task " + id + " was not found.");
        }
        boolean fullAccess = accessGuard.canFullyManageTask(authentication, task.getAssigneeRef());
        if (!fullAccess && (request.assigneeRef() != null || request.title() != null
                || request.description() != null || request.dueDate() != null)) {
            throw new AccessDeniedException("Self-scoped task updates may only change status or priority.");
        }

        Instant now = Instant.now();
        boolean reassigned = false;
        if (fullAccess && request.assigneeRef() != null && !request.assigneeRef().equals(task.getAssigneeRef())) {
            if (!accessGuard.isActiveMember(task.getProjectId(), request.assigneeRef())) {
                throw new InvalidRequestException("Assignee " + request.assigneeRef() + " is not an active member of this project.");
            }
            task.reassign(request.assigneeRef(), actor, now);
            reassigned = true;
        }
        if (fullAccess && (request.title() != null || request.description() != null || request.dueDate() != null)) {
            task.updateDetails(request.title() != null ? request.title() : task.getTitle(),
                    request.description() != null ? request.description() : task.getDescription(),
                    request.dueDate() != null ? request.dueDate() : task.getDueDate(), actor, now);
        }
        boolean statusChanged = false;
        if (request.status() != null && request.status() != task.getStatus()) {
            task.changeStatus(request.status(), actor, now);
            statusChanged = true;
        }
        if (request.priority() != null && request.priority() != task.getPriority()) {
            task.changePriority(request.priority(), actor, now);
        }

        if (reassigned) {
            outboxEventWriter.write("task.assigned.v1", task.getId(), Map.of(
                    "taskId", task.getId().toString(), "projectId", task.getProjectId().toString(),
                    "assigneeId", task.getAssigneeRef().toString()), correlationId);
        }
        if (statusChanged) {
            outboxEventWriter.write("task.status-changed.v1", task.getId(), Map.of(
                    "taskId", task.getId().toString(), "projectId", task.getProjectId().toString(),
                    "status", task.getStatus().name()), correlationId);
        }

        return toResponse(task);
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project " + projectId + " was not found.");
        }
    }

    private TaskDtos.Response toResponse(Task task) {
        return new TaskDtos.Response(task.getId(), task.getProjectId(), task.getAssigneeRef(), task.getTitle(),
                task.getDescription(), task.getStatus(), task.getPriority(), task.getDueDate(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
