package com.growdigitalbridge.project.api.dto;

import com.growdigitalbridge.project.domain.MembershipRole;
import com.growdigitalbridge.project.domain.MembershipStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ProjectMembershipDtos {

    private ProjectMembershipDtos() { }

    public record AddRequest(@NotNull UUID employeeRef, MembershipRole role) { }

    /** Exactly one of {@code role} (change role) or {@code status} (remove/reactivate) is expected per call. */
    public record UpdateRequest(MembershipRole role, MembershipStatus status) { }

    public record Response(UUID id, UUID projectId, UUID employeeRef, MembershipRole role, MembershipStatus status,
                            Instant createdAt, Instant updatedAt) { }
}
