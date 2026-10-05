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
 * One employee's Indian-statutory applicability record: PF/EPF, ESI, and Professional Tax, each
 * with its own applicability status, identifier, and effective dates. No rate, threshold, or
 * eligibility *rule* is computed or stored here (PENDING_GDB_APPROVAL, Section X) - this is
 * purely the data a real calculation would eventually need, and the gap-detection surface the
 * calculation engine checks (Section I/X). {@code pfUan}/{@code pfMemberId}/{@code esiIdentifier}
 * are sensitive per decision 14 - never log these field values.
 *
 * <p>One row per employee (unique {@code employee_ref}) - this is a current-state profile, not an
 * effective-dated history like {@link EmployeeCompensation}; the per-scheme {@code *EffectiveFrom}/
 * {@code *EffectiveTo} fields record when that specific scheme's applicability began/ended, not a
 * revision history of the row itself.
 *
 * <p>{@code taxRegime} (India Payroll V1 architecture-extension task) is a free-form, employee-
 * elected tax-regime identifier (e.g. which of the new/old regimes the employee has chosen) used
 * only to resolve a tax-regime-aware {@code StatutoryRule} version for a {@code TDS}-coded
 * component - this codebase never decides what regime identifiers exist or defaults one, per the
 * explicit instruction not to decide which tax regime GDB uses.
 */
@Entity
@Table(name = "employee_statutory_profiles")
public class EmployeeStatutoryProfile {

    @Id
    private UUID id;

    @Column(name = "employee_ref", nullable = false, unique = true)
    private UUID employeeRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "pf_status", nullable = false, length = 24)
    private StatutoryApplicabilityStatus pfStatus;

    @Column(name = "pf_uan", length = 32)
    private String pfUan;

    @Column(name = "pf_member_id", length = 32)
    private String pfMemberId;

    @Column(name = "pf_effective_from")
    private LocalDate pfEffectiveFrom;

    @Column(name = "pf_effective_to")
    private LocalDate pfEffectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "esi_status", nullable = false, length = 24)
    private StatutoryApplicabilityStatus esiStatus;

    @Column(name = "esi_identifier", length = 32)
    private String esiIdentifier;

    @Column(name = "esi_effective_from")
    private LocalDate esiEffectiveFrom;

    @Column(name = "esi_effective_to")
    private LocalDate esiEffectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "pt_status", nullable = false, length = 24)
    private StatutoryApplicabilityStatus ptStatus;

    @Column(name = "pt_jurisdiction", length = 64)
    private String ptJurisdiction;

    @Column(name = "pt_effective_from")
    private LocalDate ptEffectiveFrom;

    @Column(name = "pt_effective_to")
    private LocalDate ptEffectiveTo;

    @Column(name = "tax_regime", length = 32)
    private String taxRegime;

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

    protected EmployeeStatutoryProfile() { }

    public EmployeeStatutoryProfile(UUID id, UUID employeeRef,
                                     StatutoryApplicabilityStatus pfStatus, String pfUan, String pfMemberId,
                                     LocalDate pfEffectiveFrom, LocalDate pfEffectiveTo,
                                     StatutoryApplicabilityStatus esiStatus, String esiIdentifier,
                                     LocalDate esiEffectiveFrom, LocalDate esiEffectiveTo,
                                     StatutoryApplicabilityStatus ptStatus, String ptJurisdiction,
                                     LocalDate ptEffectiveFrom, LocalDate ptEffectiveTo,
                                     String taxRegime,
                                     String actor, Instant now) {
        this.id = id;
        this.employeeRef = employeeRef;
        this.pfStatus = pfStatus;
        this.pfUan = pfUan;
        this.pfMemberId = pfMemberId;
        this.pfEffectiveFrom = pfEffectiveFrom;
        this.pfEffectiveTo = pfEffectiveTo;
        this.esiStatus = esiStatus;
        this.esiIdentifier = esiIdentifier;
        this.esiEffectiveFrom = esiEffectiveFrom;
        this.esiEffectiveTo = esiEffectiveTo;
        this.ptStatus = ptStatus;
        this.ptJurisdiction = ptJurisdiction;
        this.ptEffectiveFrom = ptEffectiveFrom;
        this.ptEffectiveTo = ptEffectiveTo;
        this.taxRegime = taxRegime;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    /** Full replace of every field - the service layer owns which fields actually changed. */
    public void update(StatutoryApplicabilityStatus pfStatus, String pfUan, String pfMemberId,
                        LocalDate pfEffectiveFrom, LocalDate pfEffectiveTo,
                        StatutoryApplicabilityStatus esiStatus, String esiIdentifier,
                        LocalDate esiEffectiveFrom, LocalDate esiEffectiveTo,
                        StatutoryApplicabilityStatus ptStatus, String ptJurisdiction,
                        LocalDate ptEffectiveFrom, LocalDate ptEffectiveTo,
                        String taxRegime,
                        String actor, Instant now) {
        this.pfStatus = pfStatus;
        this.pfUan = pfUan;
        this.pfMemberId = pfMemberId;
        this.pfEffectiveFrom = pfEffectiveFrom;
        this.pfEffectiveTo = pfEffectiveTo;
        this.esiStatus = esiStatus;
        this.esiIdentifier = esiIdentifier;
        this.esiEffectiveFrom = esiEffectiveFrom;
        this.esiEffectiveTo = esiEffectiveTo;
        this.ptStatus = ptStatus;
        this.ptJurisdiction = ptJurisdiction;
        this.ptEffectiveFrom = ptEffectiveFrom;
        this.ptEffectiveTo = ptEffectiveTo;
        this.taxRegime = taxRegime;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeRef() { return employeeRef; }
    public StatutoryApplicabilityStatus getPfStatus() { return pfStatus; }
    public String getPfUan() { return pfUan; }
    public String getPfMemberId() { return pfMemberId; }
    public LocalDate getPfEffectiveFrom() { return pfEffectiveFrom; }
    public LocalDate getPfEffectiveTo() { return pfEffectiveTo; }
    public StatutoryApplicabilityStatus getEsiStatus() { return esiStatus; }
    public String getEsiIdentifier() { return esiIdentifier; }
    public LocalDate getEsiEffectiveFrom() { return esiEffectiveFrom; }
    public LocalDate getEsiEffectiveTo() { return esiEffectiveTo; }
    public StatutoryApplicabilityStatus getPtStatus() { return ptStatus; }
    public String getPtJurisdiction() { return ptJurisdiction; }
    public LocalDate getPtEffectiveFrom() { return ptEffectiveFrom; }
    public LocalDate getPtEffectiveTo() { return ptEffectiveTo; }
    public String getTaxRegime() { return taxRegime; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    /** True if PF is APPLICABLE/PENDING_VERIFICATION but neither identifier is recorded. */
    public boolean isPfIdentifierMissing() {
        return pfStatus != StatutoryApplicabilityStatus.NOT_APPLICABLE
                && isBlank(pfUan) && isBlank(pfMemberId);
    }

    /** True if ESI is APPLICABLE/PENDING_VERIFICATION but no identifier is recorded. */
    public boolean isEsiIdentifierMissing() {
        return esiStatus != StatutoryApplicabilityStatus.NOT_APPLICABLE && isBlank(esiIdentifier);
    }

    /** True if Professional Tax is APPLICABLE/PENDING_VERIFICATION but no jurisdiction is recorded. */
    public boolean isPtJurisdictionMissing() {
        return ptStatus != StatutoryApplicabilityStatus.NOT_APPLICABLE && isBlank(ptJurisdiction);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
