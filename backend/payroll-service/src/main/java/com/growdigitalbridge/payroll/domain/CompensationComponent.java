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
 * EmployeeCompensation} (PAYROLL_REQUIREMENTS.md Section E/F). {@code componentCode} is a
 * free-form reference to the pending {@code PayComponent} catalogue - no catalogue content is
 * seeded or assumed. {@code amount} is a fixed value only; no formula/calculation-type column is
 * added, since this task explicitly forbids implementing deduction/tax formulas.
 * {@code prorationPolicyCode} is an opaque, unresolved identifier (Section H's technical model
 * only) - no policy implementation is registered or invoked in this Phase 1 foundation.
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

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    protected CompensationComponent() { }

    public CompensationComponent(UUID id, UUID compensationId, String componentCode, CompensationComponentType componentType,
                                  BigDecimal amount, String prorationPolicyCode, String actor, Instant now) {
        this.id = id;
        this.compensationId = compensationId;
        this.componentCode = componentCode;
        this.componentType = componentType;
        this.amount = amount;
        this.prorationPolicyCode = prorationPolicyCode;
        this.createdAt = now;
        this.createdBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getCompensationId() { return compensationId; }
    public String getComponentCode() { return componentCode; }
    public CompensationComponentType getComponentType() { return componentType; }
    public BigDecimal getAmount() { return amount; }
    public String getProrationPolicyCode() { return prorationPolicyCode; }
    public Instant getCreatedAt() { return createdAt; }
}
