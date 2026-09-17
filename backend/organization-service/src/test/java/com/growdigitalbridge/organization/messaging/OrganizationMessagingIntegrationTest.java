package com.growdigitalbridge.organization.messaging;

import com.growdigitalbridge.organization.repository.ProcessedEventRepository;
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
 * Proves the consumer side for real: a genuine RabbitMQ broker delivers a message published
 * exactly as employee-service's relay would, the listener records it in the inbox, and a
 * redelivery of the identical event ID does not get processed twice.
 */
@Testcontainers
@SpringBootTest
class OrganizationMessagingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void consumesEmployeeCreatedEventExactlyOnceDespiteRedelivery() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        DomainEvent event = new DomainEvent(eventId, "employee.created.v1", 1, Instant.now(), UUID.randomUUID(),
                "employee-service", UUID.randomUUID(), Map.of("employeeId", UUID.randomUUID().toString()));

        rabbitTemplate.convertAndSend("gdb.domain.events", "employee.created.v1", event);
        awaitProcessed(eventId);

        // Simulates at-least-once redelivery of the same event.
        rabbitTemplate.convertAndSend("gdb.domain.events", "employee.created.v1", event);
        Thread.sleep(1000);

        assertThat(processedEventRepository.count()).isEqualTo(1);
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
}
