package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.StatutoryApplicabilityStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One employee's PF/ESI/Professional Tax applicability record (item 3). No rate, threshold, or
 * eligibility-calculation field exists anywhere here. {@code pfUan}/{@code pfMemberId}/
 * {@code esiIdentifier} are sensitive (decision 14) - never logged.
 */
public final class StatutoryProfileDtos {

    private StatutoryProfileDtos() { }

    /**
     * Create-or-replace (upsert) by {@code employeeRef} - there is exactly one profile per
     * employee, so no separate create/update distinction is exposed at the API level.
     */
    public record UpsertRequest(
            @NotNull StatutoryApplicabilityStatus pfStatus, @Size(max = 32) String pfUan, @Size(max = 32) String pfMemberId,
            LocalDate pfEffectiveFrom, LocalDate pfEffectiveTo,
            @NotNull StatutoryApplicabilityStatus esiStatus, @Size(max = 32) String esiIdentifier,
            LocalDate esiEffectiveFrom, LocalDate esiEffectiveTo,
            @NotNull StatutoryApplicabilityStatus ptStatus, @Size(max = 64) String ptJurisdiction,
            LocalDate ptEffectiveFrom, LocalDate ptEffectiveTo,
            @Size(max = 32) String taxRegime) { }

    public record Response(
            UUID id, UUID employeeRef,
            StatutoryApplicabilityStatus pfStatus, String pfUan, String pfMemberId,
            LocalDate pfEffectiveFrom, LocalDate pfEffectiveTo,
            StatutoryApplicabilityStatus esiStatus, String esiIdentifier,
            LocalDate esiEffectiveFrom, LocalDate esiEffectiveTo,
            StatutoryApplicabilityStatus ptStatus, String ptJurisdiction,
            LocalDate ptEffectiveFrom, LocalDate ptEffectiveTo,
            String taxRegime,
            Instant createdAt, Instant updatedAt) { }
}
