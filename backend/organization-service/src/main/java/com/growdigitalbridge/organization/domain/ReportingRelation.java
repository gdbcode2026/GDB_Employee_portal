package com.growdigitalbridge.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The sole source of truth for manager/team-scope authorization. {@code employeeRef} and
 * {@code managerEmployeeRef} are cross-service references (no FK) to the future Employee
 * service. A partial unique index enforces at most one ACTIVE relation per employee.
 */
@Entity
@Table(name = "reporting_relations")
public class ReportingRelation {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "manager_employee_ref", nullable = false)
    private UUID managerEmployeeRef;

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportingRelationStatus status;

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

    protected ReportingRelation() { }

    public ReportingRelation(UUID id, UUID employeeRef, UUID managerEmployeeRef, LocalDate effectiveStartDate, String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.managerEmployeeRef = managerEmployeeRef;
        this.effectiveStartDate = effectiveStartDate;
        this.status = ReportingRelationStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void end(LocalDate effectiveEndDate, String actor, Instant now) {
        this.effectiveEndDate = effectiveEndDate;
        this.status = ReportingRelationStatus.ENDED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public UUID getManagerEmployeeRef() { return managerEmployeeRef; }
    public LocalDate getEffectiveStartDate() { return effectiveStartDate; }
    public LocalDate getEffectiveEndDate() { return effectiveEndDate; }
    public ReportingRelationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
