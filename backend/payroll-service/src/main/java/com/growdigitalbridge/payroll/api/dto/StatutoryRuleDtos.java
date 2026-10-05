package com.growdigitalbridge.payroll.api.dto;

import com.growdigitalbridge.payroll.domain.StatutoryRuleCalculationType;
import com.growdigitalbridge.payroll.domain.StatutoryRuleStatus;
import com.growdigitalbridge.payroll.domain.StatutoryRuleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Rule Engine task DTOs. No field here carries a real PF/ESI/Professional Tax/TDS rate, slab, or
 * threshold by itself - {@code parameters} is always empty/configurable until an approved value
 * is supplied through this API, and every field is structural (item 2/17).
 */
public final class StatutoryRuleDtos {

    private StatutoryRuleDtos() { }

    /** Which fields are required depends on the owning rule's {@code calculationType} - validated in {@code StatutoryRuleService}, not here. */
    public record ParametersRequest(
            BigDecimal amount,
            @DecimalMin("0") @DecimalMax("100") BigDecimal percentage,
            @Size(max = 64) String wageBasisComponentCode,
            @DecimalMin("0") BigDecimal minWage,
            @DecimalMin("0") BigDecimal maxWage,
            @DecimalMin("0") BigDecimal cap) { }

    /**
     * Creates the next version of {@code code} (version 1 if {@code code} is new). {@code
     * ruleType}/{@code jurisdiction} must match the existing family's locked values when {@code
     * code} already exists - they are fixed for the lifetime of a rule family.
     */
    public record CreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotNull StatutoryRuleType ruleType,
            @Size(max = 64) String jurisdiction,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull StatutoryRuleCalculationType calculationType,
            @NotNull @Valid ParametersRequest parameters) { }

    /** Only permitted while the rule version is {@code DRAFT} - {@code code}/{@code ruleType}/{@code jurisdiction}/{@code ruleVersion} never change. */
    public record UpdateRequest(
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull StatutoryRuleCalculationType calculationType,
            @NotNull @Valid ParametersRequest parameters) { }

    public record Response(
            UUID id, String code, StatutoryRuleType ruleType, String jurisdiction, int ruleVersion,
            LocalDate effectiveFrom, LocalDate effectiveTo, StatutoryRuleStatus status,
            StatutoryRuleCalculationType calculationType, ParametersRequest parameters,
            Instant createdAt, Instant updatedAt) { }
}
