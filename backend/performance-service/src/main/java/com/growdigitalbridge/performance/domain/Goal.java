package com.growdigitalbridge.performance.domain;

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
 * A standing, per-employee objective. {@code employeeRef} is a cross-service reference to
 * Employee's own primary key - never an enforced database FK - and is always resolved
 * server-side from the caller's validated identity, never supplied by a client.
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 500)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GoalStatus status;

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

    protected Goal() { }

    public Goal(UUID id, UUID employeeRef, String title, String description, String target, String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.title = title;
        this.description = description;
        this.target = target;
        this.status = GoalStatus.OPEN;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void updateDetails(String title, String description, String target, String actor, Instant now) {
        this.title = title;
        this.description = description;
        this.target = target;
        touch(actor, now);
    }

    public void changeStatus(GoalStatus status, String actor, Instant now) {
        this.status = status;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTarget() { return target; }
    public GoalStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
