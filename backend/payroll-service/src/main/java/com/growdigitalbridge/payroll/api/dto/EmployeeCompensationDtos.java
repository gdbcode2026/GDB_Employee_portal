package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.CompensationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EmployeeCompensationDtos {

    private EmployeeCompensationDtos() { }

    /**
     * {@code currency}/{@code payFrequency} are deliberately absent - locked to {@code INR}/
     * {@code MONTHLY} (decisions 2/3), never accepted as free input, exactly like
     * {@code PayrollPeriodDtos.CreateRequest} derives its dates rather than accepting them.
     * {@code amount} carries no sign constraint - {@code compensation_components.amount} has none
     * (Section F) - so this does not invent one either.
     */
    public record ComponentRequest(
            @NotBlank @Size(max = 64) String componentCode,
            @NotNull CompensationComponentType componentType,
            @NotNull BigDecimal amount,
            @Size(max = 64) String prorationPolicyCode,
            @Size(max = 64) String calculationStrategyCode) { }

    public record CreateRequest(
            @NotNull UUID employeeRef,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotEmpty @Valid List<ComponentRequest> components) { }

    /** Full replace of effective dates, status, and the component list - never a partial patch. */
    public record UpdateRequest(
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull CompensationStatus status,
            @NotEmpty @Valid List<ComponentRequest> components) { }

    public record ComponentResponse(UUID id, String componentCode, CompensationComponentType componentType,
                                     BigDecimal amount, String prorationPolicyCode, String calculationStrategyCode) { }

    public record Response(UUID id, UUID employeeRef, String currency, String payFrequency,
                            LocalDate effectiveFrom, LocalDate effectiveTo, CompensationStatus status,
                            List<ComponentResponse> components, Instant createdAt, Instant updatedAt) { }
}
