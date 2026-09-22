package com.growdigitalbridge.workflow.service;

import com.growdigitalbridge.workflow.api.dto.ApprovalTaskDtos;
import com.growdigitalbridge.workflow.api.dto.PageResponse;
import com.growdigitalbridge.workflow.api.dto.WorkflowInstanceDtos;
import com.growdigitalbridge.workflow.domain.ApprovalTask;
import com.growdigitalbridge.workflow.domain.InstanceStatus;
import com.growdigitalbridge.workflow.domain.TaskStatus;
import com.growdigitalbridge.workflow.domain.WorkflowInstance;
import com.growdigitalbridge.workflow.repository.ApprovalTaskRepository;
import com.growdigitalbridge.workflow.repository.WorkflowDefinitionRepository;
import com.growdigitalbridge.workflow.repository.WorkflowInstanceRepository;
import com.growdigitalbridge.workflow.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.workflow.service.exception.ResourceNotFoundException;
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
 * Owns workflow instances: starting one, scoped visibility, and requester cancellation.
 * Task decisions (which also drive instance completion) live in {@link ApprovalTaskService}.
 */
@Service
public class WorkflowInstanceService {

    private final WorkflowInstanceRepository repository;
    private final WorkflowDefinitionRepository definitionRepository;
    private final ApprovalTaskRepository taskRepository;
    private final WorkflowAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public WorkflowInstanceService(WorkflowInstanceRepository repository, WorkflowDefinitionRepository definitionRepository,
                                    ApprovalTaskRepository taskRepository, WorkflowAccessGuard accessGuard,
                                    OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.definitionRepository = definitionRepository;
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public WorkflowInstanceDtos.Response start(WorkflowInstanceDtos.StartRequest request, String actor) {
        if (!definitionRepository.existsById(request.definitionId())) {
            throw new ResourceNotFoundException("Workflow definition " + request.definitionId() + " was not found.");
        }
        Instant now = Instant.now();
        WorkflowInstance instance = new WorkflowInstance(UUID.randomUUID(), request.definitionId(), request.subjectType(),
                request.subjectRef(), request.requesterRef(), request.dueAt(), actor, now);
        repository.save(instance);

        List<ApprovalTask> tasks = new java.util.ArrayList<>();
        int sequence = 1;
        for (UUID assigneeRef : request.assigneeRefs()) {
            ApprovalTask task = new ApprovalTask(UUID.randomUUID(), instance.getId(), sequence++, assigneeRef, actor, now);
            taskRepository.save(task);
            tasks.add(task);
        }

        return toResponse(instance, tasks);
    }

    @Transactional(readOnly = true)
    public WorkflowInstanceDtos.Response getById(UUID id, Authentication authentication) {
        WorkflowInstance instance = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance " + id + " was not found."));
        List<ApprovalTask> tasks = taskRepository.findByInstanceId(id);
        if (!canView(authentication, instance, tasks)) {
            throw new ResourceNotFoundException("Workflow instance " + id + " was not found.");
        }
        return toResponse(instance, tasks);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowInstanceDtos.Response> list(Authentication authentication, Pageable pageable) {
        WorkflowAccessGuard.ReadScope scope = accessGuard.resolveInstanceListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing workflows requires self, team, or all read scope.");
        }
        if (!scope.unrestricted() && scope.requesterRefs().isEmpty()) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        Page<WorkflowInstance> page = scope.unrestricted()
                ? repository.findAll(pageable)
                : repository.searchWithinScope(scope.requesterRefs(), pageable);
        return PageResponse.of(page.map(instance -> toResponse(instance, taskRepository.findByInstanceId(instance.getId()))));
    }

    /**
     * "Requester cancellation is permitted only while configured as cancellable"
     * (WORKFLOWS.md). Since {@code rulesJson} is not parsed, this foundation applies the
     * conservative default of always allowing the original requester (or workflow.manage) to
     * cancel while RUNNING - a default GDB can override once cancellability configuration is
     * actually interpreted.
     */
    @Transactional
    public WorkflowInstanceDtos.Response cancel(UUID id, Authentication authentication, String actor, UUID correlationId) {
        WorkflowInstance instance = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance " + id + " was not found."));
        boolean isRequester = accessGuard.resolveSelf(authentication).map(self -> self.equals(instance.getRequesterRef())).orElse(false);
        boolean canManage = accessGuard.hasAuthority(authentication, "workflow.manage");
        if (!isRequester && !canManage) {
            throw new AccessDeniedException("Only the requester or a workflow administrator may cancel this instance.");
        }
        if (instance.getStatus() != InstanceStatus.RUNNING) {
            throw new InvalidLifecycleTransitionException("Workflow instance " + id + " is not running.");
        }

        Instant now = Instant.now();
        instance.changeStatus(InstanceStatus.CANCELLED, actor, now);
        List<ApprovalTask> tasks = taskRepository.findByInstanceIdAndStatus(id, TaskStatus.PENDING);
        tasks.forEach(task -> task.cancel(actor, now));

        outboxEventWriter.write("workflow.completed.v1", instance.getId(), Map.of(
                "workflowId", instance.getId().toString(),
                "subjectType", instance.getSubjectType().name(),
                "subjectRef", instance.getSubjectRef().toString(),
                "outcome", InstanceStatus.CANCELLED.name(),
                "decisionTime", now.toString()), correlationId);

        return toResponse(instance, taskRepository.findByInstanceId(id));
    }

    private boolean canView(Authentication authentication, WorkflowInstance instance, List<ApprovalTask> tasks) {
        if (accessGuard.hasAuthority(authentication, "workflow.read.all")) {
            return true;
        }
        if (accessGuard.hasAuthority(authentication, "workflow.read.team")
                && accessGuard.isWithinCallersTeamScope(authentication, instance.getRequesterRef())) {
            return true;
        }
        if (accessGuard.hasAuthority(authentication, "workflow.read.self")) {
            var self = accessGuard.resolveSelf(authentication);
            if (self.isPresent()) {
                if (self.get().equals(instance.getRequesterRef())) {
                    return true;
                }
                return tasks.stream().anyMatch(task -> task.getAssigneeRef().equals(self.get()));
            }
        }
        return false;
    }

    private WorkflowInstanceDtos.Response toResponse(WorkflowInstance instance, List<ApprovalTask> tasks) {
        List<ApprovalTaskDtos.Response> taskResponses = tasks.stream().map(this::toResponse).toList();
        return new WorkflowInstanceDtos.Response(instance.getId(), instance.getDefinitionId(), instance.getSubjectType(),
                instance.getSubjectRef(), instance.getRequesterRef(), instance.getStatus(), instance.getDueAt(),
                taskResponses, instance.getCreatedAt(), instance.getUpdatedAt());
    }

    private ApprovalTaskDtos.Response toResponse(ApprovalTask task) {
        return new ApprovalTaskDtos.Response(task.getId(), task.getInstanceId(), task.getSequenceNumber(), task.getAssigneeRef(),
                task.getStatus(), task.getDecision(), task.getComment(), task.getDecidedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
