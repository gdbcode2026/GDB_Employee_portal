package com.growdigitalbridge.organization.api.dto;

import com.growdigitalbridge.organization.domain.TeamStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class TeamDtos {

    private TeamDtos() { }

    public record CreateRequest(
            @NotNull UUID departmentId,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 64) String code) { }

    public record UpdateRequest(
            @Size(max = 160) String name,
            @Size(max = 64) String code,
            TeamStatus status) { }

    public record Response(UUID id, UUID departmentId, String name, String code, TeamStatus status, Instant createdAt, Instant updatedAt) { }
}
