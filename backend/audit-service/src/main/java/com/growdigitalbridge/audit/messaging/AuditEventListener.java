package com.growdigitalbridge.audit.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.audit.domain.AuditEntry;
import com.growdigitalbridge.audit.domain.ProcessedEvent;
import com.growdigitalbridge.audit.repository.AuditEntryRepository;
import com.growdigitalbridge.audit.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every domain event received on the catch-all {@code audit.events.v1} queue as an
 * append-only audit entry. A message that fails processing after the configured retry
 * attempts (see application.yml's {@code spring.rabbitmq.listener.simple.retry}) is rejected
 * without requeue and lands in {@code audit.events.v1.dlq} via the queue's dead-letter config.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditEntryRepository auditEntryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public AuditEventListener(AuditEntryRepository auditEntryRepository, ProcessedEventRepository processedEventRepository,
                               ObjectMapper objectMapper) {
        this.auditEntryRepository = auditEntryRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "audit.events.v1")
    @Transactional
    public void onDomainEvent(DomainEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Ignoring already-processed event {} ({})", event.eventId(), event.eventType());
            return;
        }
        try {
            String metadataJson = objectMapper.writeValueAsString(event.payload());
            AuditEntry entry = new AuditEntry(UUID.randomUUID(), event.occurredAt(), event.producer(), event.eventType(),
                    resourceTypeOf(event.eventType()), event.aggregateId().toString(), "SUCCESS",
                    event.correlationId(), metadataJson);
            auditEntryRepository.save(entry);
            processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit metadata for event " + event.eventId(), e);
        }
    }

    private String resourceTypeOf(String eventType) {
        int dot = eventType.indexOf('.');
        String domain = dot > 0 ? eventType.substring(0, dot) : eventType;
        return Character.toUpperCase(domain.charAt(0)) + domain.substring(1);
    }
}
