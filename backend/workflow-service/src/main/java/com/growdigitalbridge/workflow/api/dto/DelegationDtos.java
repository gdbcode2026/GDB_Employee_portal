package com.growdigitalbridge.workflow.api.dto;

import com.growdigitalbridge.workflow.domain.DelegationStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class DelegationDtos {

    private DelegationDtos() { }

    public record CreateRequest(@NotNull UUID delegateRef, Instant startsAt, @NotNull Instant endsAt) { }

    public record Response(UUID id, UUID taskId, UUID delegatorRef, UUID delegateRef, Instant startsAt, Instant endsAt,
                            DelegationStatus status, Instant createdAt, Instant updatedAt) { }
}
