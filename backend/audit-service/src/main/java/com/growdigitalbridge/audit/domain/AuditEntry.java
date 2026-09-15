package com.growdigitalbridge.audit.domain;

import jakarta.persistence.*; import java.time.Instant; import java.util.UUID; import org.hibernate.annotations.Immutable;

@Entity @Immutable @Table(name = "audit_entries")
public class AuditEntry {
    @Id private UUID id; @Column(nullable = false) private Instant occurredAt; @Column(nullable = false) private String action;
    @Column(nullable = false) private String resourceType; @Column(nullable = false) private String resourceRef;
    @Column(nullable = false) private String outcome; @Column(nullable = false) private UUID correlationId;
    protected AuditEntry() { }
}
