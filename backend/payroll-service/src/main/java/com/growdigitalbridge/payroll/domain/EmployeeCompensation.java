package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Effective-dated compensation record (decision 4), managed through
 * {@code EmployeeCompensationService}/{@code EmployeeCompensationController}. {@code currency} is
 * fixed to {@code INR} (decision 2) and validated in the service layer, never accepted as
 * free-form input beyond that one locked value. {@code status} is an administrative on/off switch
 * independent of effective-dating (see {@link CompensationStatus}). Per decision 4 ("a revision is
 * a new row, never an in-place edit") and this task's immutability requirement, once a record has
 * been used by any {@code FINALIZED} {@code PayrollRun} (i.e. a finalized run's period start date
 * falls within this record's effective range), the service layer refuses any further update to it
 * or its components - enforced in {@code EmployeeCompensationService}, not here. Amounts on the
 * attached {@link CompensationComponent} rows are sensitive per decision 14 - never log this
 * entity's or its components' field values.
 */
@Entity
@Table(name = "employee_compensations")
public class EmployeeCompensation {

    public static final String LOCKED_CURRENCY = "INR";

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false)
    private UUID employeeRef;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false, length = 16)
    private PayFrequency payFrequency;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CompensationStatus status;

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

    protected EmployeeCompensation() { }

    public EmployeeCompensation(UUID id, UUID employeeRef, String currency, PayFrequency payFrequency,
                                 LocalDate effectiveFrom, LocalDate effectiveTo, String actor, Instant now) {
        this(id, employeeRef, currency, payFrequency, effectiveFrom, effectiveTo, CompensationStatus.ACTIVE, actor, now);
    }

    public EmployeeCompensation(UUID id, UUID employeeRef, String currency, PayFrequency payFrequency,
                                 LocalDate effectiveFrom, LocalDate effectiveTo, CompensationStatus status,
                                 String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.currency = currency;
        this.payFrequency = payFrequency;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = status;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    /**
     * Updates only the administrative fields a correction may legitimately touch (never the
     * amounts on attached {@link CompensationComponent} rows via this method). The service layer
     * is solely responsible for verifying this record has not yet been used by any {@code
     * FINALIZED} run before calling this - this entity performs no guard of its own, matching
     * this platform's established convention.
     */
    public void update(LocalDate effectiveFrom, LocalDate effectiveTo, CompensationStatus status, String actor, Instant now) {
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = status;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public String getCurrency() { return currency; }
    public PayFrequency getPayFrequency() { return payFrequency; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public CompensationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
