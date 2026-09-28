package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payroll's own snapshot of one {@code attendance.finalized.v1} event (Section I, decision 6).
 * Only fields the event actually carries are persisted - {@code attendanceId}, {@code
 * employeeId}, {@code workDate}, {@code status} - so nothing is inferred or fetched back from
 * Attendance Service; a non-finalized record is never consumed (the event is only ever published
 * for a finalized one). This is read-only input for {@code ProrationPolicy}; nothing here is
 * itself a proration formula.
 */
@Entity
@Table(name = "payroll_attendance_inputs")
public class PayrollAttendanceInput {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "attendance_ref", nullable = false)
    private UUID attendanceRef;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PayrollAttendanceInput() { }

    public PayrollAttendanceInput(UUID id, UUID employeeRef, LocalDate workDate, UUID attendanceRef,
                                   UUID sourceEventId, Instant receivedAt) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.workDate = workDate;
        this.attendanceRef = attendanceRef;
        this.sourceEventId = sourceEventId;
        this.receivedAt = receivedAt;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public LocalDate getWorkDate() { return workDate; }
    public UUID getAttendanceRef() { return attendanceRef; }
    public UUID getSourceEventId() { return sourceEventId; }
    public Instant getReceivedAt() { return receivedAt; }
}
