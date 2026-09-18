package com.growdigitalbridge.attendance.api.dto;

import com.growdigitalbridge.attendance.domain.WfhRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class WfhRequestDtos {

    private WfhRequestDtos() { }

    public record CreateRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 500) String reason) { }

    public record DecisionRequest(@NotNull Decision decision) { }

    public record Response(UUID id, UUID employeeRef, LocalDate startDate, LocalDate endDate, String reason,
                            WfhRequestStatus status, String decidedBy, Instant decidedAt) { }
}
