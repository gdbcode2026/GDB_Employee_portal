package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Catalogue master (PAYROLL_REQUIREMENTS.md Section F). Deliberately empty in this codebase -
 * GDB's actual pay-component catalogue is PENDING_GDB_APPROVAL (Section X) and this task
 * explicitly forbids seeding or inventing it. This table exists only so the shape a catalogue
 * row takes is fixed in the schema ahead of that approval.
 */
@Entity
@Table(name = "pay_components")
public class PayComponent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CompensationComponentType type;

    protected PayComponent() { }

    public PayComponent(UUID id, String code, String name, CompensationComponentType type) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public CompensationComponentType getType() { return type; }
}
