package com.growdigitalbridge.project.api.dto;

import com.growdigitalbridge.project.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() { }

    public record CreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 160) String name) { }

    public record UpdateRequest(
            @Size(max = 160) String name,
            ProjectStatus status) { }

    public record Response(UUID id, String code, String name, UUID ownerRef, ProjectStatus status,
                            Instant createdAt, Instant updatedAt) { }
}
