package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Recorded for one employee in a run to surface a configuration/data-quality gap (Phase 2 item 10,
 * extended by this task): {@code NO_EFFECTIVE_COMPENSATION} still means no {@link PayrollRunLine}
 * was produced for the employee; every other {@link PayrollExceptionReason} is recorded *alongside*
 * a normally-calculated line, purely for visibility. Unique per {@code (run_id, employee_ref,
 * reason)} - an employee may have more than one distinct exception in the same run (e.g. missing
 * PF identifier and missing ESI identifier at once), but never a duplicate of the same reason.
 * Carries no amount and no free-text detail beyond the fixed reason code, so nothing sensitive can
 * leak through it (decision 14). {@code status}/{@code resolvedAt}/{@code resolvedBy} are a
 * resolution-tracking addition (this task) - resolving an exception is a record-keeping action
 * only; it never retroactively recalculates the run.
 */
@Entity
@Table(name = "payroll_exceptions")
public class PayrollException {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PayrollExceptionReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayrollExceptionStatus status;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    protected PayrollException() { }

    public PayrollException(UUID id, UUID runId, UUID employeeRef, PayrollExceptionReason reason, Instant now) {
        this.id = id;
        this.runId = runId;
        this.employeeRef = employeeRef;
        this.reason = reason;
        this.status = PayrollExceptionStatus.OPEN;
        this.detectedAt = now;
    }

    public void resolve(String actor, Instant now) {
        this.status = PayrollExceptionStatus.RESOLVED;
        this.resolvedBy = actor;
        this.resolvedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public UUID getEmployeeRef() { return employeeRef; }
    public PayrollExceptionReason getReason() { return reason; }
    public PayrollExceptionStatus getStatus() { return status; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
}
