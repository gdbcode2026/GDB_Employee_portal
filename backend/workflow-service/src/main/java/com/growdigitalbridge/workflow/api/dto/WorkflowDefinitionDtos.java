package com.growdigitalbridge.workflow.api.dto;

import com.growdigitalbridge.workflow.domain.RequestType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class WorkflowDefinitionDtos {

    private WorkflowDefinitionDtos() { }

    /**
     * {@code rulesJson} is accepted and stored as an opaque JSON object - approval-stage
     * structure, approver resolution, SLA, delegation eligibility, and escalation targets are
     * GDB configuration decisions (docs/workflows/WORKFLOWS.md) that this service does not
     * parse, validate, or enforce.
     */
    public record CreateRequest(@NotNull RequestType requestType, @NotNull @Min(1) Integer definitionVersion,
                                 @NotNull Map<String, Object> rulesJson) { }

    public record Response(UUID id, RequestType requestType, int definitionVersion, Map<String, Object> rulesJson,
                            Instant createdAt, Instant updatedAt) { }
}
