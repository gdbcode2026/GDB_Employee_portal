package com.growdigitalbridge.asset.domain;

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
 * {@code employeeRef} is always resolved server-side from the caller's validated identity,
 * never a bare client-controlled authorization input. {@code workflowRef} stays null until an
 * administrator starts a Workflow Service instance against this request out-of-band (Asset
 * Service never calls Workflow to start one - see AssetRequestService); once populated, it is
 * the correlation key used to match an incoming {@code workflow.completed.v1} event back to
 * this request via {@code subjectRef}.
 */
@Entity
@Table(name = "asset_requests")
public class AssetRequest {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(nullable = false, length = 120)
    private String type;

    @Column(length = 2000)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetRequestStatus status;

    @Column(name = "workflow_ref")
    private UUID workflowRef;

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

    protected AssetRequest() { }

    public AssetRequest(UUID id, UUID employeeRef, String type, String justification, String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.type = type;
        this.justification = justification;
        this.status = AssetRequestStatus.SUBMITTED;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void applyWorkflowOutcome(AssetRequestStatus status, UUID workflowRef, String actor, Instant now) {
        this.status = status;
        this.workflowRef = workflowRef;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void cancel(String actor, Instant now) {
        this.status = AssetRequestStatus.CANCELLED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public String getType() { return type; }
    public String getJustification() { return justification; }
    public AssetRequestStatus getStatus() { return status; }
    public UUID getWorkflowRef() { return workflowRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
