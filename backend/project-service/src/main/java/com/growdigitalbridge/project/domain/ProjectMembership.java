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

@Entity
@Table(name = "project_memberships")
public class ProjectMembership {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipStatus status;

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

    protected ProjectMembership() { }

    public ProjectMembership(UUID id, UUID projectId, UUID employeeRef, MembershipRole role, String actor, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.employeeRef = employeeRef;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void reactivate(MembershipRole role, String actor, Instant now) {
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
        touch(actor, now);
    }

    public void changeRole(MembershipRole role, String actor, Instant now) {
        this.role = role;
        touch(actor, now);
    }

    public void remove(String actor, Instant now) {
        this.status = MembershipStatus.REMOVED;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getEmployeeRef() { return employeeRef; }
    public MembershipRole getRole() { return role; }
    public MembershipStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
