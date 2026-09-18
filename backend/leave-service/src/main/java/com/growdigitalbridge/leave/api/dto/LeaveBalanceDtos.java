package com.growdigitalbridge.leave.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public final class LeaveBalanceDtos {

    private LeaveBalanceDtos() { }

    public record AllocateRequest(
            @NotNull UUID employeeRef,
            @NotNull UUID leaveTypeId,
            @Min(2000) int periodYear,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal allocated) { }

    public record AdjustRequest(@NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal allocated) { }

    public record Response(UUID id, UUID employeeRef, UUID leaveTypeId, int periodYear,
                            BigDecimal allocated, BigDecimal used, BigDecimal reserved, BigDecimal available) { }
}
