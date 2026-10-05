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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One versioned, effective-dated statutory/tax rule (Configurable Statutory + Tax Rule Engine
 * task). {@code code} names a rule *family* (e.g. a PF employee-contribution rule); every row
 * sharing a {@code code} is one version of that family, numbered by {@code ruleVersion} and
 * never mutated once it leaves {@code DRAFT} ({@link StatutoryRuleStatus}) - correcting an
 * {@code ACTIVE}/{@code INACTIVE} version means creating the next version under the same
 * {@code code}, which is what keeps a historical payroll calculation reproducible even after a
 * newer version is later activated (item 1/7). {@code ruleType}/{@code jurisdiction} are fixed
 * for the lifetime of a {@code code} family (enforced by {@code StatutoryRuleService}, not here) -
 * only {@code effectiveFrom}/{@code effectiveTo}/{@code calculationType}/{@code parameters} vary
 * between versions. {@code jurisdiction} is {@code null} for a national/non-jurisdictional rule
 * (PF/ESI) and a state/jurisdiction code for a jurisdiction-aware rule (Professional Tax, item 5).
 * {@code taxRegime} (India Payroll V1 architecture-extension task) is the analogous dimension for
 * a tax-regime-aware rule (TDS): {@code null} for a regime-independent rule, or a free-form
 * regime identifier otherwise - this codebase never decides what regime identifiers exist, only
 * that one *can* be configured per rule version. {@code jurisdiction} and {@code taxRegime} each
 * get their own independent version-1-onward sequence under the same {@code code}.
 *
 * <p>{@code parameters} is a JSON-encoded {@link StatutoryRuleParameters} - the generic
 * calculation shape ({@code calculationType} says how to interpret it), never executable code or
 * a formula string (item 14: "do not store executable code/formulas supplied by users"). No row
 * created by this codebase outside of a test's explicitly-marked TEST DATA carries a real PF/
 * ESI/Professional Tax/TDS rate, slab, or threshold - every value is PENDING_GDB_APPROVAL
 * (PAYROLL_REQUIREMENTS.md Section X) until supplied through the management API.
 */
@Entity
@Table(name = "statutory_rules")
public class StatutoryRule {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 24)
    private StatutoryRuleType ruleType;

    @Column(length = 64)
    private String jurisdiction;

    @Column(name = "tax_regime", length = 32)
    private String taxRegime;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatutoryRuleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 24)
    private StatutoryRuleCalculationType calculationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String parameters;

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

    protected StatutoryRule() { }

    /** Always created as {@link StatutoryRuleStatus#DRAFT} - {@code activate()} is the only way to make a version live. */
    public StatutoryRule(UUID id, String code, StatutoryRuleType ruleType, String jurisdiction, String taxRegime, int ruleVersion,
                          LocalDate effectiveFrom, LocalDate effectiveTo, StatutoryRuleCalculationType calculationType,
                          String parameters, String actor, Instant now) {
        this.id = id;
        this.code = code;
        this.ruleType = ruleType;
        this.jurisdiction = jurisdiction;
        this.taxRegime = taxRegime;
        this.ruleVersion = ruleVersion;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = StatutoryRuleStatus.DRAFT;
        this.calculationType = calculationType;
        this.parameters = parameters;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    /** Only permitted while {@code status == DRAFT} - enforced by {@code StatutoryRuleService}, not here. */
    public void updateDraft(LocalDate effectiveFrom, LocalDate effectiveTo, StatutoryRuleCalculationType calculationType,
                             String parameters, String actor, Instant now) {
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.calculationType = calculationType;
        this.parameters = parameters;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void activate(String actor, Instant now) {
        this.status = StatutoryRuleStatus.ACTIVE;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void deactivate(String actor, Instant now) {
        this.status = StatutoryRuleStatus.INACTIVE;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public StatutoryRuleType getRuleType() { return ruleType; }
    public String getJurisdiction() { return jurisdiction; }
    public String getTaxRegime() { return taxRegime; }
    public int getRuleVersion() { return ruleVersion; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public StatutoryRuleStatus getStatus() { return status; }
    public StatutoryRuleCalculationType getCalculationType() { return calculationType; }
    public String getParameters() { return parameters; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
