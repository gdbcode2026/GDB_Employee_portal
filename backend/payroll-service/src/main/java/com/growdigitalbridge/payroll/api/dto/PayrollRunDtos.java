package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.PayrollRunStatus;
import com.growdigitalbridge.payroll.domain.PayrollRunType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class PayrollRunDtos {

    private PayrollRunDtos() { }

    public record CreateRequest(@NotNull UUID periodId) { }

    public record Response(UUID id, UUID periodId, PayrollRunType runType, UUID correctsRunId, PayrollRunStatus status,
                            int employeeCount, String initiatedBy, String approvedBy, Instant approvedAt,
                            Instant finalizedAt, Instant createdAt, Instant updatedAt) { }
}
