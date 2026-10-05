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
import java.util.List;
import java.util.UUID;

/**
 * Rule Engine task DTOs, extended by the India Payroll V1 architecture-extension task
 * (brackets, {@code taxRegime}). No field here carries a real PF/ESI/Professional Tax/TDS rate,
 * slab, or threshold by itself - {@code parameters} is always empty/configurable until an
 * approved value is supplied through this API, and every field is structural (item 2/17).
 */
public final class StatutoryRuleDtos {

    private StatutoryRuleDtos() { }

    /**
     * One ordered income band for {@code SLAB_BASED}/{@code PROGRESSIVE_TAX} rules. See {@code
     * StatutoryRuleBracket} for the exact semantics and {@code StatutoryRuleService} for the
     * structural validation (contiguity, ordering, exactly-one-of-fixedAmount-or-percentage) this
     * DTO alone does not enforce.
     */
    public record BracketRequest(
            @NotNull Integer order,
            @NotNull @DecimalMin("0") BigDecimal lowerBound,
            BigDecimal upperBound,
            @DecimalMin("0") BigDecimal fixedAmount,
            @DecimalMin("0") @DecimalMax("100") BigDecimal percentage) { }

    /** Which fields are required depends on the owning rule's {@code calculationType} - validated in {@code StatutoryRuleService}, not here. */
    public record ParametersRequest(
            BigDecimal amount,
            @DecimalMin("0") @DecimalMax("100") BigDecimal percentage,
            @Size(max = 64) String wageBasisComponentCode,
            @DecimalMin("0") BigDecimal minWage,
            @DecimalMin("0") BigDecimal maxWage,
            @DecimalMin("0") BigDecimal cap,
            @Valid List<BracketRequest> brackets) { }

    /**
     * Creates the next version of {@code code} (version 1 if {@code code} is new). {@code
     * ruleType} must match the existing family's locked value when {@code code} already exists;
     * {@code jurisdiction} and {@code taxRegime} each get their own independent version-1-onward
     * sequence under the same {@code code} (item 5, and the India Payroll V1 architecture-
     * extension task's tax-regime-aware resolution).
     */
    public record CreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotNull StatutoryRuleType ruleType,
            @Size(max = 64) String jurisdiction,
            @Size(max = 32) String taxRegime,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull StatutoryRuleCalculationType calculationType,
            @NotNull @Valid ParametersRequest parameters) { }

    /** Only permitted while the rule version is {@code DRAFT} - {@code code}/{@code ruleType}/{@code jurisdiction}/{@code taxRegime}/{@code ruleVersion} never change. */
    public record UpdateRequest(
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull StatutoryRuleCalculationType calculationType,
            @NotNull @Valid ParametersRequest parameters) { }

    public record Response(
            UUID id, String code, StatutoryRuleType ruleType, String jurisdiction, String taxRegime, int ruleVersion,
            LocalDate effectiveFrom, LocalDate effectiveTo, StatutoryRuleStatus status,
            StatutoryRuleCalculationType calculationType, ParametersRequest parameters,
            Instant createdAt, Instant updatedAt) { }
}
