package com.growdigitalbridge.asset.api.dto;

import com.growdigitalbridge.asset.domain.AssetRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AssetRequestDtos {

    private AssetRequestDtos() { }

    /** {@code type} is freeform text: no requestable-asset-type vocabulary is documented, so none is invented here. */
    public record CreateRequest(@NotBlank @Size(max = 120) String type, @Size(max = 2000) String justification) { }

    public record Response(UUID id, UUID employeeRef, String type, String justification, AssetRequestStatus status,
                            UUID workflowRef, Instant createdAt, Instant updatedAt) { }
}
