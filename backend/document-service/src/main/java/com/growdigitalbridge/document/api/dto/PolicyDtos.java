package com.growdigitalbridge.document.api.dto;

import com.growdigitalbridge.document.domain.PolicyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PolicyDtos {

    private PolicyDtos() { }

    public record CreateRequest(@NotNull UUID documentId, @NotBlank @Size(max = 200) String title) { }

    public record UpdateRequest(@Size(max = 200) String title, PolicyStatus status) { }

    public record Response(UUID id, UUID documentId, String title, PolicyStatus status, Instant createdAt, Instant updatedAt) { }
}
