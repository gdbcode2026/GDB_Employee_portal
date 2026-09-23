package com.growdigitalbridge.asset.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AssetAssignmentDtos {

    private AssetAssignmentDtos() { }

    public record CreateRequest(@NotNull UUID employeeRef) { }

    public record ReturnRequest(@Size(max = 2000) String conditionNotes) { }

    public record Response(UUID id, UUID assetId, UUID employeeRef, Instant assignedAt, Instant returnedAt,
                            String conditionNotes, Instant createdAt, Instant updatedAt) { }
}
