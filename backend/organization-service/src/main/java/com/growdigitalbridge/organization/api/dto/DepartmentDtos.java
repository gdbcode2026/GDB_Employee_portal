package com.growdigitalbridge.organization.api.dto;

import com.growdigitalbridge.organization.domain.DepartmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DepartmentDtos {

    private DepartmentDtos() { }

    public record CreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 64) String code) { }

    public record UpdateRequest(
            @Size(max = 160) String name,
            @Size(max = 64) String code,
            DepartmentStatus status) { }

    public record Response(UUID id, String name, String code, DepartmentStatus status, Instant createdAt, Instant updatedAt) { }
}
