package com.growdigitalbridge.workflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.workflow.api.dto.WorkflowDefinitionDtos;
import com.growdigitalbridge.workflow.api.dto.WorkflowInstanceDtos;
import com.growdigitalbridge.workflow.client.EmployeeClient;
import com.growdigitalbridge.workflow.client.OrganizationClient;
import com.growdigitalbridge.workflow.domain.RequestType;
import com.growdigitalbridge.workflow.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
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
 * Proves both directions of messaging against a real broker: cancelling an instance publishes
 * {@code workflow.completed.v1} through the outbox relay, and a real {@code
 * employee.deactivated.v1} message delivered to the shared exchange is idempotently recorded
 * by {@link EmployeeEventListener}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WorkflowMessagingIntegrationTest {

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
    void cancellingAnInstanceIsPublishedToTheDomainEventsExchange() throws Exception {
        UUID requester = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(java.util.Optional.of(requester));

        MvcResult definitionResult = mockMvc.perform(post("/api/v1/workflows/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkflowDefinitionDtos.CreateRequest(RequestType.LEAVE, 1, java.util.Map.of()))))
                .andExpect(status().isCreated()).andReturn();
        UUID definitionId = objectMapper.readValue(definitionResult.getResponse().getContentAsString(), WorkflowDefinitionDtos.Response.class).id();

        MvcResult instanceResult = mockMvc.perform(post("/api/v1/workflows")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkflowInstanceDtos.StartRequest(
                                definitionId, RequestType.LEAVE, UUID.randomUUID(), requester, List.of(UUID.randomUUID()), null))))
                .andExpect(status().isCreated()).andReturn();
        WorkflowInstanceDtos.Response instance = objectMapper.readValue(instanceResult.getResponse().getContentAsString(), WorkflowInstanceDtos.Response.class);

        Queue testQueue = new Queue("test.workflow.completed.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("workflow.completed.v1"));

        mockMvc.perform(post("/api/v1/workflows/" + instance.id() + "/cancel")
                        .with(jwt().authorities(new SimpleGrantedAuthority("workflow.decide.assigned"))))
                .andExpect(status().isOk());

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("workflow.completed.v1");
        assertThat(received.aggregateId()).isEqualTo(instance.id());
        assertThat(received.producer()).isEqualTo("workflow-service");
        assertThat(received.payload()).containsEntry("outcome", "CANCELLED");
    }

    @Test
    void employeeDeactivatedEventIsConsumedIdempotently() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID employeeRef = UUID.randomUUID();
        DomainEvent event = new DomainEvent(eventId, "employee.deactivated.v1", 1, Instant.now(), UUID.randomUUID(),
                "employee-service", employeeRef, Map.of("employeeId", employeeRef.toString(), "effectiveAt", Instant.now().toString()));

        rabbitTemplate.convertAndSend("gdb.domain.events", "employee.deactivated.v1", event);
        rabbitTemplate.convertAndSend("gdb.domain.events", "employee.deactivated.v1", event);

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
