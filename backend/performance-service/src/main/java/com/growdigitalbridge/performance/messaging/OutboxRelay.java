package com.growdigitalbridge.performance.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.performance.domain.OutboxEvent;
import com.growdigitalbridge.performance.repository.OutboxEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the transactional outbox and publishes unpublished rows to {@code gdb.domain.events}.
 * A row that fails to publish (broker unavailable, etc.) is simply left unpublished and
 * retried on the next poll - the outbox itself is the retry mechanism for the relay's own
 * at-least-once delivery to the broker. Consumers are responsible for idempotent handling
 * of any resulting duplicate delivery.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String EXCHANGE = "gdb.domain.events";
    private static final String PRODUCER = "performance-service";
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${gdb.messaging.outbox-relay.fixed-delay-ms:5000}")
    @Transactional
    public void relay() {
        repository.findByPublishedAtIsNullOrderByOccurredAtAsc(PageRequest.of(0, BATCH_SIZE)).forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<Map<String, Object>>() { });
            DomainEvent envelope = new DomainEvent(event.getId(), event.getEventType(), 1, event.getOccurredAt(),
                    event.getCorrelationId(), PRODUCER, event.getAggregateId(), payload);
            rabbitTemplate.convertAndSend(EXCHANGE, event.getEventType(), envelope);
            event.markPublished(Instant.now());
        } catch (Exception e) {
            log.warn("Failed to publish outbox event {} (type {}); left unpublished for retry on next poll: {}",
                    event.getId(), event.getEventType(), e.getMessage());
        }
    }
}
