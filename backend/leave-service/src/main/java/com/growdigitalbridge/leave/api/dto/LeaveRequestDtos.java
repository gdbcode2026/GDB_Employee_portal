package com.growdigitalbridge.leave.api.dto;

import com.growdigitalbridge.leave.domain.LeaveRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LeaveRequestDtos {

    private LeaveRequestDtos() { }

    public record CreateRequest(
            @NotNull UUID leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason) { }

    public record DecisionRequest(@NotNull Decision decision) { }

    public record Response(UUID id, UUID employeeRef, UUID leaveTypeId, LocalDate startDate, LocalDate endDate,
                            BigDecimal units, String reason, LeaveRequestStatus status, String decidedBy, Instant decidedAt) { }
}
