package com.growdigitalbridge.attendance.api.dto;

import com.growdigitalbridge.attendance.domain.RegularizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RegularizationDtos {

    private RegularizationDtos() { }

    public record CreateRequest(
            @NotNull LocalDate workDate,
            Instant requestedCheckInAt,
            Instant requestedCheckOutAt,
            @NotBlank @Size(max = 500) String reason) { }

    public record DecisionRequest(@NotNull Decision decision) { }

    public record Response(UUID id, UUID employeeRef, LocalDate workDate, Instant requestedCheckInAt,
                            Instant requestedCheckOutAt, String reason, RegularizationStatus status,
                            String decidedBy, Instant decidedAt) { }
}
