package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.PayrollPeriodStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PayrollPeriodDtos {

    private PayrollPeriodDtos() { }

    /**
     * {@code startDate}/{@code endDate} are deliberately absent - they are always derived from
     * {@code year}/{@code month} (decision 3: monthly periods only), never accepted as free
     * input. {@code cutOffDate} is optional; no cut-off policy is documented.
     */
    public record CreateRequest(@NotNull @Min(2000) Integer year, @NotNull @Min(1) Integer month, LocalDate cutOffDate) { }

    public record Response(UUID id, int year, int month, LocalDate startDate, LocalDate endDate, LocalDate cutOffDate,
                            PayrollPeriodStatus status, Instant createdAt, Instant updatedAt) { }
}
