package com.growdigitalbridge.attendance.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.attendance.api.dto.AttendanceDtos;
import com.growdigitalbridge.attendance.client.EmployeeClient;
import com.growdigitalbridge.attendance.client.OrganizationClient;
import com.growdigitalbridge.attendance.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves both directions of messaging against a real broker: finalize publishes
 * {@code attendance.finalized.v1} through the outbox relay, and a real {@code leave.approved.v1}
 * message delivered to the shared exchange is idempotently recorded by {@link LeaveEventListener}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AttendanceMessagingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @DynamicPropertySource
    static void fastRelay(DynamicPropertyRegistry registry) {
        registry.add("gdb.messaging.outbox-relay.fixed-delay-ms", () -> "300");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private TopicExchange domainEventsExchange;
    @Autowired private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private EmployeeClient employeeClient;

    @MockitoBean
    private OrganizationClient organizationClient;

    @Test
    void finalizingAttendanceIsPublishedToTheDomainEventsExchange() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId), Optional.of(managerId));
        when(organizationClient.resolveTeamScope(managerId)).thenReturn(Set.of(employeeId));

        Queue testQueue = new Queue("test.attendance.finalized.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("attendance.finalized.v1"));

        MvcResult created = mockMvc.perform(post("/api/v1/attendance/check-ins")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.create.self"))))
                .andExpect(status().isOk()).andReturn();
        AttendanceDtos.Response record = objectMapper.readValue(created.getResponse().getContentAsString(), AttendanceDtos.Response.class);

        mockMvc.perform(post("/api/v1/attendance/" + record.id() + "/finalize")
                        .with(jwt().authorities(new SimpleGrantedAuthority("attendance.finalize.team"))))
                .andExpect(status().isOk());

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("attendance.finalized.v1");
        assertThat(received.aggregateId()).isEqualTo(record.id());
        assertThat(received.producer()).isEqualTo("attendance-service");
        assertThat(received.payload()).containsEntry("attendanceId", record.id().toString());
    }

    @Test
    void leaveApprovedEventIsConsumedIdempotently() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID employeeRef = UUID.randomUUID();
        DomainEvent event = new DomainEvent(eventId, "leave.approved.v1", 1, Instant.now(), UUID.randomUUID(),
                "leave-service", employeeRef, Map.of("employeeId", employeeRef.toString(), "requestId", UUID.randomUUID().toString()));

        rabbitTemplate.convertAndSend("gdb.domain.events", "leave.approved.v1", event);
        rabbitTemplate.convertAndSend("gdb.domain.events", "leave.approved.v1", event);

        assertThat(awaitProcessed(eventId)).isTrue();
    }

    private boolean awaitProcessed(UUID eventId) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (processedEventRepository.existsById(eventId)) {
                return true;
            }
            Thread.sleep(300);
        }
        return false;
    }

    private DomainEvent awaitMessage(String queueName) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            Object message = rabbitTemplate.receiveAndConvert(queueName);
            if (message instanceof DomainEvent event) {
                return event;
            }
            Thread.sleep(300);
        }
        return null;
    }
}
