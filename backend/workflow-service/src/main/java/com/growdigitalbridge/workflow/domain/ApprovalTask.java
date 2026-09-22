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

@Entity
@Table(name = "approval_tasks")
public class ApprovalTask {

    @Id
    private UUID id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "assignee_ref", nullable = false)
    private UUID assigneeRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Decision decision;

    @Column(length = 2000)
    private String comment;

    @Column(name = "decided_at")
    private Instant decidedAt;

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

    protected ApprovalTask() { }

    public ApprovalTask(UUID id, UUID instanceId, int sequenceNumber, UUID assigneeRef, String actor, Instant now) {
        this.id = id;
        this.instanceId = instanceId;
        this.sequenceNumber = sequenceNumber;
        this.assigneeRef = assigneeRef;
        this.status = TaskStatus.PENDING;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void decide(Decision decision, String comment, String actor, Instant now) {
        this.decision = decision;
        this.comment = comment;
        this.status = TaskStatus.DECIDED;
        this.decidedAt = now;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void cancel(String actor, Instant now) {
        this.status = TaskStatus.CANCELLED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getInstanceId() { return instanceId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public UUID getAssigneeRef() { return assigneeRef; }
    public TaskStatus getStatus() { return status; }
    public Decision getDecision() { return decision; }
    public String getComment() { return comment; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
