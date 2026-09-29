package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One finalized, generated payslip per employee per run (PAYROLL_REQUIREMENTS.md Section E/F,
 * decision 13). Carries only an opaque {@code documentRef} pointing at the Document Service
 * record holding the actual PDF - no binary is ever stored here or anywhere in Payroll. Never
 * updated once created (immutable, matching decision 8's finalized-run immutability); a
 * corrected/adjustment run produces its own distinct {@code Payslip} row via its own {@code
 * run_id}, never a mutation of this one.
 */
@Entity
@Table(name = "payslips")
public class Payslip {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "document_ref", nullable = false)
    private UUID documentRef;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected Payslip() { }

    public Payslip(UUID id, UUID employeeRef, UUID runId, UUID periodId, UUID documentRef, Instant generatedAt) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.runId = runId;
        this.periodId = periodId;
        this.documentRef = documentRef;
        this.generatedAt = generatedAt;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public UUID getRunId() { return runId; }
    public UUID getPeriodId() { return periodId; }
    public UUID getDocumentRef() { return documentRef; }
    public Instant getGeneratedAt() { return generatedAt; }
}
