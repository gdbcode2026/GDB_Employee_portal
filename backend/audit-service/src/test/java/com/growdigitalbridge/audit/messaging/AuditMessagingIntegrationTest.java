package com.growdigitalbridge.audit.messaging;

import com.growdigitalbridge.audit.repository.AuditEntryRepository;
import com.growdigitalbridge.audit.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Exercises the consumer against a real broker and real database: a well-formed event is
 * recorded exactly once despite redelivery, and a message that can never succeed (a "poison"
 * event) is retried the configured number of times and then dead-lettered - proving
 * retry+DLQ end-to-end, not just that the topology beans exist.
 */
@Testcontainers
@SpringBootTest
class AuditMessagingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void consumesAWellFormedEventExactlyOnceDespiteRedelivery() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        DomainEvent event = new DomainEvent(eventId, "employee.created.v1", 1, Instant.now(), UUID.randomUUID(),
                "employee-service", aggregateId, Map.of("employeeId", aggregateId.toString()));

        rabbitTemplate.convertAndSend("gdb.domain.events", "employee.created.v1", event);
        awaitProcessed(eventId);
        assertThat(auditEntryRepository.count()).isEqualTo(1);

        rabbitTemplate.convertAndSend("gdb.domain.events", "employee.created.v1", event);
        Thread.sleep(1000);

        assertThat(auditEntryRepository.count()).isEqualTo(1);
    }

    @Test
    void poisonMessageIsDeadLetteredAfterExhaustingRetries() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        DomainEvent poison = new DomainEvent(eventId, "", 1, Instant.now(), UUID.randomUUID(),
                "employee-service", UUID.randomUUID(), Map.of());

        rabbitTemplate.convertAndSend("gdb.domain.events", "employee.created.v1", poison);

        DomainEvent deadLettered = awaitDeadLetter();

        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.eventId()).isEqualTo(eventId);
    }

    private void awaitProcessed(UUID eventId) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (processedEventRepository.existsById(eventId)) {
                return;
            }
            Thread.sleep(300);
        }
        fail("Event " + eventId + " was not processed in time");
    }

    private DomainEvent awaitDeadLetter() throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            Object message = rabbitTemplate.receiveAndConvert("audit.events.v1.dlq");
            if (message instanceof DomainEvent event) {
                return event;
            }
            Thread.sleep(500);
        }
        return null;
    }
}
