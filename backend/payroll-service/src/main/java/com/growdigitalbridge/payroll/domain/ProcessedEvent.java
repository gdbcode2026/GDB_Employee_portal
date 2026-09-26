package com.growdigitalbridge.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbox record proving a given event ID has already been handled - the idempotency guard for
 * consumers. No {@code @RabbitListener} is registered in this Phase 1 foundation (consuming
 * {@code attendance.finalized.v1}/{@code leave.approved.v1} is calculation-pipeline work,
 * Phase 2/5 per PAYROLL_REQUIREMENTS.md Section Y); this table/entity exists now, per Section F,
 * so that a future consumer can reuse the platform's standard inbox pattern immediately.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() { }

    public ProcessedEvent(UUID eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public UUID getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
