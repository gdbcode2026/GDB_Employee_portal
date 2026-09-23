package com.growdigitalbridge.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code employeeRef} is a cross-service reference to Employee Service's own identifier, never
 * an enforced database FK, per DATABASE.md's {@code *_ref} convention. A database-level partial
 * unique index (see V1 migration) guarantees at most one open (unreturned) assignment per asset.
 */
@Entity
@Table(name = "asset_assignments")
public class AssetAssignment {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "condition_notes", length = 2000)
    private String conditionNotes;

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

    protected AssetAssignment() { }

    public AssetAssignment(UUID id, UUID assetId, UUID employeeRef, Instant assignedAt, String actor, Instant now) {
        this.id = id;
        this.assetId = assetId;
        this.employeeRef = employeeRef;
        this.assignedAt = assignedAt;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void recordReturn(Instant returnedAt, String conditionNotes, String actor, Instant now) {
        this.returnedAt = returnedAt;
        this.conditionNotes = conditionNotes;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return returnedAt == null;
    }

    public UUID getId() { return id; }
    public UUID getAssetId() { return assetId; }
    public UUID getEmployeeRef() { return employeeRef; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public String getConditionNotes() { return conditionNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
