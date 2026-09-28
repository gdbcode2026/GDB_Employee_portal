package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single earning/deduction/employer-contribution line attached to an {@link
 * EmployeeCompensation} (PAYROLL_REQUIREMENTS.md Section E/F). {@code componentCode} identifies
 * a row in the "Common India Payroll V1 Baseline" {@code PayComponent} catalogue (product
 * baseline component types only - never a GDB-specific rate or eligibility rule).
 * {@code calculationStrategyCode} and {@code prorationPolicyCode} are opaque, named strategy
 * identifiers (Section H's configurable-policy technical model, extended by Phase 2's
 * {@code com.growdigitalbridge.payroll.calculation} package): the calculation engine looks each
 * one up in a registry and falls back to a safe, no-formula default
 * ({@code FixedAmountStrategy}/{@code NoOpProrationPolicy}) when unset. Neither field stores a
 * rate, threshold, or formula itself - only which pluggable strategy implementation applies.
 * {@code amount} is sensitive per decision 14 - never log this field's value.
 */
@Entity
@Table(name = "compensation_components")
public class CompensationComponent {

    @Id
    private UUID id;

    @Column(name = "compensation_id", nullable = false)
    private UUID compensationId;

    @Column(name = "component_code", nullable = false, length = 64)
    private String componentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 24)
    private CompensationComponentType componentType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "proration_policy_code", length = 64)
    private String prorationPolicyCode;

    @Column(name = "calculation_strategy_code", length = 64)
    private String calculationStrategyCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    protected CompensationComponent() { }

    public CompensationComponent(UUID id, UUID compensationId, String componentCode, CompensationComponentType componentType,
                                  BigDecimal amount, String prorationPolicyCode, String actor, Instant now) {
        this(id, compensationId, componentCode, componentType, amount, prorationPolicyCode, null, actor, now);
    }

    public CompensationComponent(UUID id, UUID compensationId, String componentCode, CompensationComponentType componentType,
                                  BigDecimal amount, String prorationPolicyCode, String calculationStrategyCode,
                                  String actor, Instant now) {
        this.id = id;
        this.compensationId = compensationId;
        this.componentCode = componentCode;
        this.componentType = componentType;
        this.amount = amount;
        this.prorationPolicyCode = prorationPolicyCode;
        this.calculationStrategyCode = calculationStrategyCode;
        this.createdAt = now;
        this.createdBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getCompensationId() { return compensationId; }
    public String getComponentCode() { return componentCode; }
    public CompensationComponentType getComponentType() { return componentType; }
    public BigDecimal getAmount() { return amount; }
    public String getProrationPolicyCode() { return prorationPolicyCode; }
    public String getCalculationStrategyCode() { return calculationStrategyCode; }
    public Instant getCreatedAt() { return createdAt; }
}
