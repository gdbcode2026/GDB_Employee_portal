package com.growdigitalbridge.employee.domain;

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

@Entity
@Table(name = "employments")
public class Employment {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "job_title", nullable = false, length = 160)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 24)
    private EmploymentType employmentType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmploymentStatus status;

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

    protected Employment() { }

    public Employment(UUID id, UUID employeeId, String jobTitle, EmploymentType employmentType,
                       LocalDate startDate, String actor, Instant now) {
        this.id = id;
        this.employeeId = employeeId;
        this.jobTitle = jobTitle;
        this.employmentType = employmentType;
        this.startDate = startDate;
        this.status = EmploymentStatus.ACTIVE;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void updateDetails(String jobTitle, EmploymentType employmentType, String actor, Instant now) {
        this.jobTitle = jobTitle;
        this.employmentType = employmentType;
        touch(actor, now);
    }

    public void end(LocalDate endDate, String actor, Instant now) {
        this.endDate = endDate;
        this.status = EmploymentStatus.ENDED;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public String getJobTitle() { return jobTitle; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public EmploymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
