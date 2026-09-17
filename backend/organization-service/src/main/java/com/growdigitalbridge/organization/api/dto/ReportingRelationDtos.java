package com.growdigitalbridge.organization.api.dto;

import com.growdigitalbridge.organization.domain.ReportingRelationStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportingRelationDtos {

    private ReportingRelationDtos() { }

    public record CreateRequest(
            @NotNull UUID employeeRef,
            @NotNull UUID managerEmployeeRef,
            @NotNull LocalDate effectiveStartDate) { }

    public record EndRequest(@NotNull LocalDate effectiveEndDate) { }

    public record Response(UUID id, UUID employeeRef, UUID managerEmployeeRef,
                            LocalDate effectiveStartDate, LocalDate effectiveEndDate,
                            ReportingRelationStatus status) { }

    public record ScopeResponse(UUID managerEmployeeRef, List<UUID> employeeRefs) { }
}
