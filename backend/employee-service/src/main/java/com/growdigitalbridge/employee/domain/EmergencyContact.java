package com.growdigitalbridge.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emergency_contacts")
public class EmergencyContact {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 64)
    private String relationship;

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

    protected EmergencyContact() { }

    public EmergencyContact(UUID id, UUID employeeId, String name, String phone, String relationship, String actor, Instant now) {
        this.id = id;
        this.employeeId = employeeId;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getRelationship() { return relationship; }
}
