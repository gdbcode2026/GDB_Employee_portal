package com.growdigitalbridge.organization.domain;

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
 * Directory grouping under a department. Deliberately holds no employee membership or
 * manager reference - authorization scope comes only from {@link ReportingRelation}.
 */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TeamStatus status;

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

    protected Team() { }

    public Team(UUID id, UUID departmentId, String name, String code, String actor, Instant now) {
        this.id = id;
        this.departmentId = departmentId;
        this.name = name;
        this.code = code;
        this.status = TeamStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void rename(String name, String actor, Instant now) {
        this.name = name;
        touch(actor, now);
    }

    public void recode(String code, String actor, Instant now) {
        this.code = code;
        touch(actor, now);
    }

    public void changeStatus(TeamStatus status, String actor, Instant now) {
        this.status = status;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDepartmentId() { return departmentId; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public TeamStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
