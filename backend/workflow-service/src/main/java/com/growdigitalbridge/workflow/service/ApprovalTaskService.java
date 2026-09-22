package com.growdigitalbridge.workflow.service;

import com.growdigitalbridge.workflow.api.dto.ApprovalTaskDtos;
import com.growdigitalbridge.workflow.api.dto.DelegationDtos;
import com.growdigitalbridge.workflow.api.dto.PageResponse;
import com.growdigitalbridge.workflow.domain.ApprovalTask;
import com.growdigitalbridge.workflow.domain.Decision;
import com.growdigitalbridge.workflow.domain.Delegation;
import com.growdigitalbridge.workflow.domain.DelegationStatus;
import com.growdigitalbridge.workflow.domain.InstanceStatus;
import com.growdigitalbridge.workflow.domain.TaskStatus;
import com.growdigitalbridge.workflow.domain.WorkflowInstance;
import com.growdigitalbridge.workflow.repository.ApprovalTaskRepository;
import com.growdigitalbridge.workflow.repository.DelegationRepository;
import com.growdigitalbridge.workflow.repository.WorkflowInstanceRepository;
import com.growdigitalbridge.workflow.service.exception.ConflictException;
import com.growdigitalbridge.workflow.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.workflow.service.exception.InvalidRequestException;
import com.growdigitalbridge.workflow.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns approval task decisions and delegations. Deciding a task never re-evaluates or
 * re-derives {@code rulesJson}: whether a stage requires one-or-all approvers is exactly the
 * kind of GDB-configurable policy docs/workflows/WORKFLOWS.md says this service must not
 * invent. The conservative, documented-consistent default applied here is: any REJECTED
 * decision immediately ends the instance ("rejection ends the instance" - WORKFLOWS.md,
 * verbatim), and the instance is only APPROVED once every task created for it has been
 * individually APPROVED.
 */
@Service
public class ApprovalTaskService {

    private final ApprovalTaskRepository repository;
    private final WorkflowInstanceRepository instanceRepository;
    private final DelegationRepository delegationRepository;
    private final WorkflowAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public ApprovalTaskService(ApprovalTaskRepository repository, WorkflowInstanceRepository instanceRepository,
                                DelegationRepository delegationRepository, WorkflowAccessGuard accessGuard,
                                OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.instanceRepository = instanceRepository;
        this.delegationRepository = delegationRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApprovalTaskDtos.Response> listMine(Authentication authentication, Pageable pageable) {
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        Page<ApprovalTask> page = repository.searchForAssignee(self, TaskStatus.PENDING, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public ApprovalTaskDtos.Response decide(UUID instanceId, UUID taskId, Authentication authentication,
                                             ApprovalTaskDtos.DecisionRequest request, String actor, UUID correlationId) {
        ApprovalTask task = requireTask(instanceId, taskId);
        WorkflowInstance instance = requireInstance(instanceId);
        if (instance.getStatus() != InstanceStatus.RUNNING) {
            throw new InvalidLifecycleTransitionException("Workflow instance " + instanceId + " is not running.");
        }
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new InvalidLifecycleTransitionException("Task " + taskId + " has already been decided.");
        }
        if (!isCurrentlyAuthorizedFor(authentication, task)) {
            throw new ResourceNotFoundException("Task " + taskId + " was not found.");
        }

        Instant now = Instant.now();
        task.decide(request.decision(), request.comment(), actor, now);

        if (request.decision() == Decision.REJECTED) {
            instance.changeStatus(InstanceStatus.REJECTED, actor, now);
            repository.findByInstanceIdAndStatus(instanceId, TaskStatus.PENDING).forEach(pending -> pending.cancel(actor, now));
            publishCompletion(instance, InstanceStatus.REJECTED, now, correlationId);
        } else {
            boolean allApproved = repository.findByInstanceId(instanceId).stream()
                    .allMatch(t -> t.getStatus() == TaskStatus.DECIDED && t.getDecision() == Decision.APPROVED);
            if (allApproved) {
                instance.changeStatus(InstanceStatus.APPROVED, actor, now);
                publishCompletion(instance, InstanceStatus.APPROVED, now, correlationId);
            }
        }

        return toResponse(task);
    }

    @Transactional
    public DelegationDtos.Response createDelegation(UUID instanceId, UUID taskId, Authentication authentication,
                                                      DelegationDtos.CreateRequest request, String actor) {
        ApprovalTask task = requireTask(instanceId, taskId);
        WorkflowInstance instance = requireInstance(instanceId);
        if (instance.getStatus() != InstanceStatus.RUNNING || task.getStatus() != TaskStatus.PENDING) {
            throw new InvalidLifecycleTransitionException("Task " + taskId + " is not open for delegation.");
        }
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (!task.getAssigneeRef().equals(self)) {
            // Only the original assignee may delegate - no re-delegation chains, per the
            // flagged minimum-decision rationale in WorkflowAccessGuard.
            throw new ResourceNotFoundException("Task " + taskId + " was not found.");
        }
        if (!delegationRepository.findByTaskIdAndStatus(taskId, DelegationStatus.ACTIVE).isEmpty()) {
            throw new ConflictException("Task " + taskId + " already has an active delegation.");
        }
        Instant now = Instant.now();
        Instant startsAt = request.startsAt() != null ? request.startsAt() : now;
        if (!request.endsAt().isAfter(startsAt)) {
            throw new InvalidRequestException("The delegation end time must be after its start time.");
        }

        Delegation delegation = new Delegation(UUID.randomUUID(), taskId, self, request.delegateRef(), startsAt, request.endsAt(), actor, now);
        delegationRepository.save(delegation);
        return toResponse(delegation);
    }

    private boolean isCurrentlyAuthorizedFor(Authentication authentication, ApprovalTask task) {
        UUID self = accessGuard.resolveSelf(authentication).orElse(null);
        if (self == null) {
            return false;
        }
        if (task.getAssigneeRef().equals(self)) {
            return true;
        }
        Instant now = Instant.now();
        return delegationRepository.findByTaskIdAndStatus(task.getId(), DelegationStatus.ACTIVE).stream()
                .anyMatch(delegation -> delegation.getDelegateRef().equals(self) && delegation.isActiveAt(now));
    }

    private void publishCompletion(WorkflowInstance instance, InstanceStatus outcome, Instant decisionTime, UUID correlationId) {
        outboxEventWriter.write("workflow.completed.v1", instance.getId(), Map.of(
                "workflowId", instance.getId().toString(),
                "subjectType", instance.getSubjectType().name(),
                "subjectRef", instance.getSubjectRef().toString(),
                "outcome", outcome.name(),
                "decisionTime", decisionTime.toString()), correlationId);
    }

    private ApprovalTask requireTask(UUID instanceId, UUID taskId) {
        ApprovalTask task = repository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + taskId + " was not found."));
        if (!task.getInstanceId().equals(instanceId)) {
            throw new ResourceNotFoundException("Task " + taskId + " was not found.");
        }
        return task;
    }

    private WorkflowInstance requireInstance(UUID instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance " + instanceId + " was not found."));
    }

    private ApprovalTaskDtos.Response toResponse(ApprovalTask task) {
        return new ApprovalTaskDtos.Response(task.getId(), task.getInstanceId(), task.getSequenceNumber(), task.getAssigneeRef(),
                task.getStatus(), task.getDecision(), task.getComment(), task.getDecidedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }

    private DelegationDtos.Response toResponse(Delegation delegation) {
        return new DelegationDtos.Response(delegation.getId(), delegation.getTaskId(), delegation.getDelegatorRef(),
                delegation.getDelegateRef(), delegation.getStartsAt(), delegation.getEndsAt(), delegation.getStatus(),
                delegation.getCreatedAt(), delegation.getUpdatedAt());
    }
}
