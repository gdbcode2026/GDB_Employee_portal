package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.PayrollExceptionReason;
import com.growdigitalbridge.payroll.domain.PayrollExceptionStatus;
import java.time.Instant;
import java.util.UUID;

public final class PayrollExceptionDtos {

    private PayrollExceptionDtos() { }

    public record Response(UUID id, UUID runId, UUID employeeRef, PayrollExceptionReason reason,
                            PayrollExceptionStatus status, Instant detectedAt, Instant resolvedAt, String resolvedBy) { }
}
