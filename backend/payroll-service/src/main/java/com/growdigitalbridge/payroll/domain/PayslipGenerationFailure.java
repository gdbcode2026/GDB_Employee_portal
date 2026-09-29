package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Recorded when payslip generation fails for one employee within a finalized run (item 7: "do
 * not silently mark the payslip as successful... record the failure safely"). Not part of the
 * original specification - a new, minimal entity mirroring {@link PayrollException}'s precedent
 * of surfacing a technical/process problem without inventing a business resolution for it.
 * Retrying (calling {@code POST /payroll/runs/{id}/finalize} again on an already-`FINALIZED`
 * run) re-attempts generation for this employee and replaces this row on either outcome - it is
 * upserted per {@code (run_id, employee_ref)}, never accumulated.
 */
@Entity
@Table(name = "payslip_generation_failures")
public class PayslipGenerationFailure {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "failure_type", length = 160)
    private String failureType;

    @Column(name = "failure_message", length = 2000)
    private String failureMessage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PayslipGenerationFailure() { }

    public PayslipGenerationFailure(UUID id, UUID runId, UUID employeeRef, String failureType, String failureMessage, Instant occurredAt) {
        this.id = id;
        this.runId = runId;
        this.employeeRef = employeeRef;
        this.failureType = failureType;
        this.failureMessage = failureMessage;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public UUID getEmployeeRef() { return employeeRef; }
    public String getFailureType() { return failureType; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getOccurredAt() { return occurredAt; }
}
