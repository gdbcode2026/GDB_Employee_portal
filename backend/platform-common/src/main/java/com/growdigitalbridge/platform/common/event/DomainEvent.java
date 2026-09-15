package com.growdigitalbridge.platform.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Versioned, minimal event envelope. Domain services must supply redacted payloads only. */
public record DomainEvent(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                          UUID correlationId, String producer, UUID aggregateId,
                          Map<String, Object> payload) { }
