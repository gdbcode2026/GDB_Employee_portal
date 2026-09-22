package com.growdigitalbridge.workflow.api.dto;

import com.growdigitalbridge.workflow.domain.Decision;
import com.growdigitalbridge.workflow.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ApprovalTaskDtos {

    private ApprovalTaskDtos() { }

    public record DecisionRequest(@NotNull Decision decision, @Size(max = 2000) String comment) { }

    public record Response(UUID id, UUID instanceId, int sequenceNumber, UUID assigneeRef, TaskStatus status,
                            Decision decision, String comment, Instant decidedAt, Instant createdAt, Instant updatedAt) { }
}
