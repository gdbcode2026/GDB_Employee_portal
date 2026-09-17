package com.growdigitalbridge.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit trail row. {@code @Immutable} forbids updates after insert - audit entries are never revised. */
@Entity
@Immutable
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_ref", length = 128)
    private String actorRef;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 128)
    private String resourceType;

    @Column(name = "resource_ref", nullable = false, length = 128)
    private String resourceRef;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson;

    protected AuditEntry() { }

    public AuditEntry(UUID id, Instant occurredAt, String actorRef, String action, String resourceType,
                       String resourceRef, String outcome, UUID correlationId, String metadataJson) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.actorRef = actorRef;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceRef = resourceRef;
        this.outcome = outcome;
        this.correlationId = correlationId;
        this.metadataJson = metadataJson;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActorRef() { return actorRef; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceRef() { return resourceRef; }
    public String getOutcome() { return outcome; }
    public UUID getCorrelationId() { return correlationId; }
    public String getMetadataJson() { return metadataJson; }
}
