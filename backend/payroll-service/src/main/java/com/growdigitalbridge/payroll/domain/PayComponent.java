package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Catalogue master (PAYROLL_REQUIREMENTS.md Section F), managed through
 * {@code PayComponentService}/{@code PayComponentController}. GDB's actual pay-component
 * catalogue *content* (real rates, amounts, eligibility) is PENDING_GDB_APPROVAL (Section X) and
 * this task explicitly forbids seeding or inventing it - this entity carries only the structural
 * fields (code, name, type, active) the Common India Payroll V1 Baseline already locks the shape
 * of; no rate/amount/threshold field exists anywhere on it.
 */
@Entity
@Table(name = "pay_components")
public class PayComponent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CompensationComponentType type;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 128)
    private String updatedBy;

    @Version
    private long version;

    protected PayComponent() { }

    public PayComponent(UUID id, String code, String name, CompensationComponentType type, boolean active,
                         String actor, Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.active = active;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    /** {@code code}/{@code type} are never updatable once created - only display name and active status may change. */
    public void update(String name, boolean active, String actor, Instant now) {
        this.name = name;
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public CompensationComponentType getType() { return type; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
