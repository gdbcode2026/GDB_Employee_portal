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
 * Recorded instead of a {@link PayrollRunLine} for one employee in a run (Phase 2, item 10):
 * "keep the existing business decision unresolved... do not automatically skip or block the
 * entire run." The run still reaches {@code CALCULATED} with these employees excluded from its
 * lines, so HR/Finance can see and act on the exception list before approving - nothing here
 * decides whether that is ultimately correct policy. Carries no amount and no free-text detail
 * beyond the fixed reason code, so nothing sensitive can leak through it (decision 14).
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

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected PayrollException() { }

    public PayrollException(UUID id, UUID runId, UUID employeeRef, PayrollExceptionReason reason, Instant now) {
        this.id = id;
        this.runId = runId;
        this.employeeRef = employeeRef;
        this.reason = reason;
        this.detectedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public UUID getEmployeeRef() { return employeeRef; }
    public PayrollExceptionReason getReason() { return reason; }
    public Instant getDetectedAt() { return detectedAt; }
}
