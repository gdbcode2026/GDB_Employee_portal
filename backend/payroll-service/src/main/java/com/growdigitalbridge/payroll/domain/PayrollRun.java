package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One payroll run for one period (PAYROLL_REQUIREMENTS.md Section D/E). {@code employeeSnapshot}
 * is captured once, at DRAFT creation, from {@link com.growdigitalbridge.payroll.client.EmployeeClient}
 * (Section I) - it is never re-queried, so later Employee Service changes cannot silently alter
 * an in-flight run's population. {@code startProcessing}/{@code markCalculated}/{@code
 * markCalculationFailed} are the Phase 2 calculation lifecycle (see {@code
 * com.growdigitalbridge.payroll.calculation.PayrollCalculationEngine} for where actual
 * per-employee calculation happens - this entity only records the resulting status). Once
 * {@code FINALIZED}, no method on this class mutates state again (decision 8).
 */
@Entity
@Table(name = "payroll_runs")
public class PayrollRun {

    @Id
    private UUID id;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 16)
    private PayrollRunType runType;

    @Column(name = "corrects_run_id")
    private UUID correctsRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollRunStatus status;

    /**
     * EAGER, not LAZY: {@code PayrollRunService.process} deliberately reads this run outside any
     * transaction after orchestrating several separate ones (see that class's Javadoc), so a lazy
     * collection would throw {@code LazyInitializationException} on every response. This
     * collection is small (one row per snapshot employee) and always needed immediately anyway.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "payroll_run_employees", joinColumns = @JoinColumn(name = "run_id"))
    @Column(name = "employee_ref", nullable = false)
    private Set<UUID> employeeSnapshot = new LinkedHashSet<>();

    @Column(name = "initiated_by", length = 128)
    private String initiatedBy;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

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

    protected PayrollRun() { }

    public PayrollRun(UUID id, UUID periodId, Set<UUID> employeeSnapshot, String actor, Instant now) {
        this.id = id;
        this.periodId = periodId;
        this.runType = PayrollRunType.REGULAR;
        this.correctsRunId = null;
        this.status = PayrollRunStatus.DRAFT;
        this.employeeSnapshot = new LinkedHashSet<>(employeeSnapshot);
        this.initiatedBy = actor;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    /**
     * DRAFT/REJECTED/CALCULATION_FAILED -&gt; PROCESSING. Committed independently of whether the
     * calculation that follows succeeds, so a run visibly shows "in progress" for its duration.
     * The caller (service layer) is responsible for verifying the current status permits this
     * transition before calling - this entity performs no guard of its own, matching this
     * platform's established convention (e.g. {@code LeaveRequest}).
     */
    public void startProcessing(String actor, Instant now) {
        this.status = PayrollRunStatus.PROCESSING;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /** PROCESSING -&gt; CALCULATED, once {@code PayrollCalculationEngine} has produced every result row. */
    public void markCalculated(String actor, Instant now) {
        this.status = PayrollRunStatus.CALCULATED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /**
     * PROCESSING -&gt; CALCULATION_FAILED. Written in a transaction separate from the failed
     * calculation attempt, so this status change survives that attempt's rollback (decision 14:
     * failures are recorded safely, never silently swallowed). {@code process} accepts this
     * status as a valid re-entry point, exactly like DRAFT/REJECTED.
     */
    public void markCalculationFailed(String actor, Instant now) {
        this.status = PayrollRunStatus.CALCULATION_FAILED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /** CALCULATED -&gt; PENDING_APPROVAL. */
    public void submitForApproval(String actor, Instant now) {
        this.status = PayrollRunStatus.PENDING_APPROVAL;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /** PENDING_APPROVAL -&gt; APPROVED. Self-approval prevention is enforced by the service layer. */
    public void approve(String actor, Instant now) {
        this.status = PayrollRunStatus.APPROVED;
        this.approvedBy = actor;
        this.approvedAt = now;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /** PENDING_APPROVAL -&gt; REJECTED. A rejected run re-enters processing via {@link #startProcessing}. */
    public void reject(String actor, Instant now) {
        this.status = PayrollRunStatus.REJECTED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /** APPROVED -&gt; FINALIZED. Terminal (decision 8): no method mutates this run again afterward. */
    public void finalizeRun(String actor, Instant now) {
        this.status = PayrollRunStatus.FINALIZED;
        this.finalizedAt = now;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getPeriodId() { return periodId; }
    public PayrollRunType getRunType() { return runType; }
    public UUID getCorrectsRunId() { return correctsRunId; }
    public PayrollRunStatus getStatus() { return status; }
    public Set<UUID> getEmployeeSnapshot() { return Set.copyOf(employeeSnapshot); }
    public String getInitiatedBy() { return initiatedBy; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
