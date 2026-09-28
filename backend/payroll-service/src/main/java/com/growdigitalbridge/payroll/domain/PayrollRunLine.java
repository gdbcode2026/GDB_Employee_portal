package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One calculated result per employee per run (PAYROLL_REQUIREMENTS.md Section E/F, Phase 2).
 * Written exactly once per {@code (run_id, employee_ref)} by {@code PayrollCalculationEngine}
 * and never updated afterward - a reprocess deletes and recreates the row for a not-yet-FINALIZED
 * run rather than mutating it in place, so every persisted line is always an immutable snapshot
 * of the calculation that produced it (reproducibility, Section G). {@code componentBreakdown} is
 * structured JSON (one entry per {@code CompensationComponent} applied), not a running total, so
 * the exact inputs to {@code grossPay}/{@code totalDeductions}/{@code totalEmployerContributions}/
 * {@code netPay} remain inspectable after the fact. All amount fields are sensitive per decision
 * 14 - never log them.
 */
@Entity
@Table(name = "payroll_run_lines")
public class PayrollRunLine {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "gross_pay", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossPay;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "total_employer_contributions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEmployerContributions;

    @Column(name = "net_pay", nullable = false, precision = 14, scale = 2)
    private BigDecimal netPay;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_breakdown", nullable = false, columnDefinition = "jsonb")
    private String componentBreakdown;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    protected PayrollRunLine() { }

    public PayrollRunLine(UUID id, UUID runId, UUID employeeRef, BigDecimal grossPay, BigDecimal totalDeductions,
                           BigDecimal totalEmployerContributions, BigDecimal netPay, String componentBreakdown,
                           String actor, Instant now) {
        this.id = id;
        this.runId = runId;
        this.employeeRef = employeeRef;
        this.grossPay = grossPay;
        this.totalDeductions = totalDeductions;
        this.totalEmployerContributions = totalEmployerContributions;
        this.netPay = netPay;
        this.componentBreakdown = componentBreakdown;
        this.calculatedAt = now;
        this.createdBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public UUID getEmployeeRef() { return employeeRef; }
    public BigDecimal getGrossPay() { return grossPay; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public BigDecimal getTotalEmployerContributions() { return totalEmployerContributions; }
    public BigDecimal getNetPay() { return netPay; }
    public String getComponentBreakdown() { return componentBreakdown; }
    public Instant getCalculatedAt() { return calculatedAt; }
}
