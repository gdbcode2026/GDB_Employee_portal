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
 * "A valid delegation transfers task authority for a bounded period and retains both
 * delegator and delegate in audit history" (WORKFLOWS.md) - the delegator is never stripped
 * of their own authority; the delegate simply gains it too, for the bounded window.
 */
@Entity
@Table(name = "delegations")
public class Delegation {

    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "delegator_ref", nullable = false)
    private UUID delegatorRef;

    @Column(name = "delegate_ref", nullable = false)
    private UUID delegateRef;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DelegationStatus status;

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

    protected Delegation() { }

    public Delegation(UUID id, UUID taskId, UUID delegatorRef, UUID delegateRef, Instant startsAt, Instant endsAt,
                       String actor, Instant now) {
        this.id = id;
        this.taskId = taskId;
        this.delegatorRef = delegatorRef;
        this.delegateRef = delegateRef;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = DelegationStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public boolean isActiveAt(Instant instant) {
        return status == DelegationStatus.ACTIVE && !instant.isBefore(startsAt) && !instant.isAfter(endsAt);
    }

    public void end(String actor, Instant now) {
        this.status = DelegationStatus.ENDED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public UUID getDelegatorRef() { return delegatorRef; }
    public UUID getDelegateRef() { return delegateRef; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public DelegationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
