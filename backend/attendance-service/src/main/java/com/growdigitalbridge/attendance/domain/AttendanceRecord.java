package com.growdigitalbridge.attendance.domain;

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
 * One check-in/check-out day for one employee. {@code employeeRef} is a cross-service
 * reference to Employee's own primary key - never an enforced database FK (see
 * docs/database/DATABASE.md's {@code *_ref} convention) - and is always resolved server-side
 * from the caller's validated identity, never supplied by a client as an authorization input.
 */
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AttendanceStatus status;

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

    protected AttendanceRecord() { }

    public AttendanceRecord(UUID id, UUID employeeRef, LocalDate workDate, Instant checkInAt, String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.workDate = workDate;
        this.checkInAt = checkInAt;
        this.status = AttendanceStatus.DRAFT;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void checkOut(Instant checkOutAt, String actor, Instant now) {
        this.checkOutAt = checkOutAt;
        touch(actor, now);
    }

    public void applyRegularization(Instant requestedCheckInAt, Instant requestedCheckOutAt, String actor, Instant now) {
        if (requestedCheckInAt != null) {
            this.checkInAt = requestedCheckInAt;
        }
        if (requestedCheckOutAt != null) {
            this.checkOutAt = requestedCheckOutAt;
        }
        touch(actor, now);
    }

    public void finalizeRecord(String actor, Instant now) {
        this.status = AttendanceStatus.FINALIZED;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public LocalDate getWorkDate() { return workDate; }
    public Instant getCheckInAt() { return checkInAt; }
    public Instant getCheckOutAt() { return checkOutAt; }
    public AttendanceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
