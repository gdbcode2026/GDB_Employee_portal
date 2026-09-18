package com.growdigitalbridge.project.domain;

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
 * A project owned by an employee. {@code ownerRef} is a cross-service reference to Employee's
 * own primary key - never an enforced database FK (see docs/database/DATABASE.md's {@code
 * *_ref} convention) - and is always resolved server-side from the caller's validated
 * identity when creating a project, never supplied by a client as an authorization input.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "owner_ref", nullable = false)
    private UUID ownerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectStatus status;

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

    protected Project() { }

    public Project(UUID id, String code, String name, UUID ownerRef, String actor, Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.ownerRef = ownerRef;
        this.status = ProjectStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void updateDetails(String name, String actor, Instant now) {
        this.name = name;
        touch(actor, now);
    }

    public void changeStatus(ProjectStatus status, String actor, Instant now) {
        this.status = status;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getOwnerRef() { return ownerRef; }
    public ProjectStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
