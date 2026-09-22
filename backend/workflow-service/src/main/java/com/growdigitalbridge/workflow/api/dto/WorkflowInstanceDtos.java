package com.growdigitalbridge.workflow.api.dto;

import com.growdigitalbridge.workflow.domain.InstanceStatus;
import com.growdigitalbridge.workflow.domain.RequestType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkflowInstanceDtos {

    private WorkflowInstanceDtos() { }

    /**
     * {@code assigneeRefs} is explicit assignment only: WORKFLOWS.md documents role/manager-
     * chain/named-group resolution as additional, GDB-configurable approver-resolution modes,
     * none of which this foundation implements (doing so would mean inventing the actual
     * resolution policy). The caller must already know who the approvers are.
     */
    public record StartRequest(@NotNull UUID definitionId, @NotNull RequestType subjectType, @NotNull UUID subjectRef,
                                @NotNull UUID requesterRef, @NotEmpty List<UUID> assigneeRefs, Instant dueAt) { }

    public record Response(UUID id, UUID definitionId, RequestType subjectType, UUID subjectRef, UUID requesterRef,
                            InstanceStatus status, Instant dueAt, List<ApprovalTaskDtos.Response> tasks,
                            Instant createdAt, Instant updatedAt) { }
}
