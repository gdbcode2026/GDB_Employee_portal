package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payroll's own snapshot of one {@code leave.approved.v1} event (Section I, decision 6). Only
 * fields the event actually carries are persisted - {@code requestId}, {@code employeeId},
 * {@code approvedUnits} - so nothing is inferred or fetched back from Leave Service; a
 * non-approved request is never consumed (the event is only ever published on approval). Leave
 * Service's event does not carry the request's date range, so this snapshot cannot yet be
 * filtered to a specific payroll period - a flagged data-availability gap, not an invented
 * assumption. This is read-only input for {@code ProrationPolicy}; nothing here is itself a
 * proration formula.
 */
@Entity
@Table(name = "payroll_leave_inputs")
public class PayrollLeaveInput {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "leave_request_ref", nullable = false)
    private UUID leaveRequestRef;

    @Column(name = "approved_units", nullable = false, precision = 12, scale = 2)
    private BigDecimal approvedUnits;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PayrollLeaveInput() { }

    public PayrollLeaveInput(UUID id, UUID employeeRef, UUID leaveRequestRef, BigDecimal approvedUnits,
                              UUID sourceEventId, Instant receivedAt) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.leaveRequestRef = leaveRequestRef;
        this.approvedUnits = approvedUnits;
        this.sourceEventId = sourceEventId;
        this.receivedAt = receivedAt;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public UUID getLeaveRequestRef() { return leaveRequestRef; }
    public BigDecimal getApprovedUnits() { return approvedUnits; }
    public UUID getSourceEventId() { return sourceEventId; }
    public Instant getReceivedAt() { return receivedAt; }
}
