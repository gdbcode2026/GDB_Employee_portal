package com.growdigitalbridge.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-employee, per-type, per-year allocation. {@code reserved} tracks units held by
 * SUBMITTED requests (not yet decided); {@code used} tracks units consumed by APPROVED
 * requests. Concurrency-safe reservation is required per MICROSERVICES.md - see
 * LeaveBalanceRepository's pessimistic-lock lookup used by LeaveRequestService.
 */
@Entity
@Table(name = "leave_balances")
public class LeaveBalance {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allocated;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal used = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal reserved = BigDecimal.ZERO;

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

    protected LeaveBalance() { }

    public LeaveBalance(UUID id, UUID employeeRef, UUID leaveTypeId, int periodYear, BigDecimal allocated, String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.leaveTypeId = leaveTypeId;
        this.periodYear = periodYear;
        this.allocated = allocated;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public BigDecimal available() {
        return allocated.subtract(used).subtract(reserved);
    }

    public void reserve(BigDecimal units, String actor, Instant now) {
        this.reserved = this.reserved.add(units);
        touch(actor, now);
    }

    public void release(BigDecimal units, String actor, Instant now) {
        this.reserved = this.reserved.subtract(units);
        touch(actor, now);
    }

    public void consume(BigDecimal units, String actor, Instant now) {
        this.reserved = this.reserved.subtract(units);
        this.used = this.used.add(units);
        touch(actor, now);
    }

    public void adjustAllocation(BigDecimal allocated, String actor, Instant now) {
        this.allocated = allocated;
        touch(actor, now);
    }

    private void touch(String actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public UUID getLeaveTypeId() { return leaveTypeId; }
    public int getPeriodYear() { return periodYear; }
    public BigDecimal getAllocated() { return allocated; }
    public BigDecimal getUsed() { return used; }
    public BigDecimal getReserved() { return reserved; }
}
