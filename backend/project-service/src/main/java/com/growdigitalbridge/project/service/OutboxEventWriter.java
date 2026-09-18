package com.growdigitalbridge.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.project.domain.OutboxEvent;
import com.growdigitalbridge.project.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes a domain event to the outbox in the same transaction as the mutation it describes. */
@Component
public class OutboxEventWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void write(String eventType, UUID aggregateId, Map<String, Object> payload, UUID correlationId) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            repository.save(new OutboxEvent(UUID.randomUUID(), eventType, aggregateId, json, correlationId, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for event " + eventType, e);
        }
    }
}
