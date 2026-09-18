package com.growdigitalbridge.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Static reference data, Flyway-seeded; no management API exists in this phase (see V1 migration). */
@Entity
@Table(name = "leave_types")
public class LeaveType {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected LeaveType() { }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
