package com.growdigitalbridge.performance.api.dto;

import com.growdigitalbridge.performance.domain.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class GoalDtos {

    private GoalDtos() { }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Size(max = 500) String target) { }

    /** Partial update: any field left null is unchanged. */
    public record UpdateRequest(
            @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Size(max = 500) String target,
            GoalStatus status) { }

    public record Response(UUID id, UUID employeeRef, String title, String description, String target,
                            GoalStatus status, Instant createdAt, Instant updatedAt) { }
}
