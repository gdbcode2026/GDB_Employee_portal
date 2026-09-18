package com.growdigitalbridge.performance.api.dto;

import com.growdigitalbridge.performance.domain.ReviewCycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ReviewCycleDtos {

    private ReviewCycleDtos() { }

    public record CreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) { }

    public record UpdateRequest(
            @Size(max = 160) String name,
            LocalDate startDate,
            LocalDate endDate,
            ReviewCycleStatus status) { }

    public record Response(UUID id, String name, LocalDate startDate, LocalDate endDate, ReviewCycleStatus status,
                            Instant createdAt, Instant updatedAt) { }
}
