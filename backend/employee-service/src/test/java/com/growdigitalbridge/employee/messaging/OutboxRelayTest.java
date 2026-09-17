package com.growdigitalbridge.employee.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.employee.domain.OutboxEvent;
import com.growdigitalbridge.employee.repository.OutboxEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OutboxRelay relay() {
        return new OutboxRelay(repository, rabbitTemplate, objectMapper);
    }

    private OutboxEvent event(String eventType) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("employeeId", UUID.randomUUID().toString()));
        return new OutboxEvent(UUID.randomUUID(), eventType, UUID.randomUUID(), payload, UUID.randomUUID(), Instant.now());
    }

    @Test
    void publishesUnpublishedEventsAndMarksThemPublished() throws Exception {
        OutboxEvent event = event("employee.created.v1");
        when(repository.findByPublishedAtIsNullOrderByOccurredAtAsc(any())).thenReturn(List.of(event));

        relay().relay();

        verify(rabbitTemplate).convertAndSend(eq("gdb.domain.events"), eq("employee.created.v1"), any(DomainEvent.class));
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void leavesEventUnpublishedForRetryWhenBrokerPublishFails() throws Exception {
        OutboxEvent event = event("employee.created.v1");
        when(repository.findByPublishedAtIsNullOrderByOccurredAtAsc(any())).thenReturn(List.of(event));
        doThrow(new AmqpException("broker unavailable") { })
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        relay().relay();

        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void oneFailingEventDoesNotBlockOthersInTheSameBatch() throws Exception {
        OutboxEvent failing = event("employee.created.v1");
        OutboxEvent succeeding = event("employee.updated.v1");
        when(repository.findByPublishedAtIsNullOrderByOccurredAtAsc(any())).thenReturn(List.of(failing, succeeding));
        doThrow(new AmqpException("broker unavailable") { })
                .when(rabbitTemplate).convertAndSend(eq("gdb.domain.events"), eq("employee.created.v1"), any(Object.class));

        relay().relay();

        assertThat(failing.getPublishedAt()).isNull();
        assertThat(succeeding.getPublishedAt()).isNotNull();
    }
}
