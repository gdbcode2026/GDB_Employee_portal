package com.growdigitalbridge.expense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code employeeRef} is always resolved server-side from the caller's validated identity,
 * never a bare client-controlled authorization input. {@code total} is server-computed as the
 * sum of the claim's {@link ExpenseLine} amounts, never independently client-supplied, to
 * avoid inventing a claim/line-total reconciliation rule nothing documents. {@code
 * workflowRef} stays null unless an administrator starts a Workflow Service instance against
 * this claim out-of-band (mirroring Asset Service's identical treatment of AssetRequest); it
 * is the correlation key used to match an incoming {@code workflow.completed.v1} event back to
 * this claim via {@code subjectRef}.
 */
@Entity
@Table(name = "expense_claims")
public class ExpenseClaim {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExpenseClaimStatus status;

    @Column(name = "workflow_ref")
    private UUID workflowRef;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

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

    protected ExpenseClaim() { }

    public ExpenseClaim(UUID id, UUID employeeRef, String currency, BigDecimal total, String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.currency = currency;
        this.total = total;
        this.status = ExpenseClaimStatus.DRAFT;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void updateDraft(String currency, BigDecimal total, String actor, Instant now) {
        this.currency = currency;
        this.total = total;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void submit(String actor, Instant now) {
        this.status = ExpenseClaimStatus.SUBMITTED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void cancel(String actor, Instant now) {
        this.status = ExpenseClaimStatus.CANCELLED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void decide(ExpenseClaimStatus decision, UUID workflowRef, String actor, Instant now) {
        this.status = decision;
        this.workflowRef = workflowRef;
        this.decidedBy = actor;
        this.decidedAt = now;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void reimburse(String actor, Instant now) {
        this.status = ExpenseClaimStatus.REIMBURSED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotal() { return total; }
    public ExpenseClaimStatus getStatus() { return status; }
    public UUID getWorkflowRef() { return workflowRef; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
