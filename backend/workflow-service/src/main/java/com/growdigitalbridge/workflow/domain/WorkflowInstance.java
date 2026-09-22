package com.growdigitalbridge.workflow.domain;

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
 * {@code subjectRef} is a cross-service reference to the owning domain's own request record
 * (e.g. a LeaveRequest id) - never an enforced database FK, per DATABASE.md's {@code *_ref}
 * convention - and {@code requesterRef} is always resolved server-side from the caller's
 * validated identity, never supplied as a bare client-controlled authorization input.
 */
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

    @Id
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 32)
    private RequestType subjectType;

    @Column(name = "subject_ref", nullable = false)
    private UUID subjectRef;

    @Column(name = "requester_ref", nullable = false)
    private UUID requesterRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstanceStatus status;

    @Column(name = "due_at")
    private Instant dueAt;

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

    protected WorkflowInstance() { }

    public WorkflowInstance(UUID id, UUID definitionId, RequestType subjectType, UUID subjectRef, UUID requesterRef,
                             Instant dueAt, String actor, Instant now) {
        this.id = id;
        this.definitionId = definitionId;
        this.subjectType = subjectType;
        this.subjectRef = subjectRef;
        this.requesterRef = requesterRef;
        this.status = InstanceStatus.RUNNING;
        this.dueAt = dueAt;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void changeStatus(InstanceStatus status, String actor, Instant now) {
        this.status = status;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDefinitionId() { return definitionId; }
    public RequestType getSubjectType() { return subjectType; }
    public UUID getSubjectRef() { return subjectRef; }
    public UUID getRequesterRef() { return requesterRef; }
    public InstanceStatus getStatus() { return status; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
