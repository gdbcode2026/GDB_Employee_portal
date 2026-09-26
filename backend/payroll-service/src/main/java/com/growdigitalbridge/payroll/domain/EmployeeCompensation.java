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
 * Effective-dated compensation record (decision 4). No REST API exposes this entity in Phase 1 -
 * PAYROLL_REQUIREMENTS.md Section O documents no compensation-management endpoint at all, so
 * none is invented here; this is schema/foundation only, ready for Phase 2's calculation
 * pipeline to read. {@code currency} is fixed to {@code INR} (decision 2) and validated in
 * {@code EmployeeCompensationRepository}/service layer, never accepted as free-form input beyond
 * that one locked value. Amounts on the attached {@link CompensationComponent} rows are
 * sensitive per decision 14 - never log this entity's or its components' field values.
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
        this.id = id;
        this.employeeRef = employeeRef;
        this.currency = currency;
        this.payFrequency = payFrequency;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public String getCurrency() { return currency; }
    public PayFrequency getPayFrequency() { return payFrequency; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
