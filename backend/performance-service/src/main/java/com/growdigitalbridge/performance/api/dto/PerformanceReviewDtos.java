package com.growdigitalbridge.performance.api.dto;

import com.growdigitalbridge.performance.domain.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PerformanceReviewDtos {

    private PerformanceReviewDtos() { }

    public record CreateRequest(@NotNull UUID cycleId, @NotNull UUID employeeRef, @NotNull UUID reviewerRef) { }

    /**
     * {@code rating} is deliberately unconstrained free text - no rating scale or scoring
     * formula is documented anywhere in this repository, and none is invented here.
     */
    public record SubmitRequest(@Size(max = 100) String rating, @Size(max = 4000) String comments) { }

    public record Response(UUID id, UUID cycleId, UUID employeeRef, UUID reviewerRef, String rating, String comments,
                            ReviewStatus status, Instant submittedAt, Instant createdAt, Instant updatedAt) { }
}
