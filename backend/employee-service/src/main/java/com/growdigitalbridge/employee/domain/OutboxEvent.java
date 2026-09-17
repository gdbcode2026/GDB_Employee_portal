package com.growdigitalbridge.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox row, written in the same transaction as the domain mutation it
 * describes. {@link com.growdigitalbridge.employee.messaging.OutboxRelay} polls rows where
 * {@code publishedAt} is null and publishes them to {@code gdb.domain.events}.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() { }

    public OutboxEvent(UUID id, String eventType, UUID aggregateId, String payload, UUID correlationId, Instant occurredAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
