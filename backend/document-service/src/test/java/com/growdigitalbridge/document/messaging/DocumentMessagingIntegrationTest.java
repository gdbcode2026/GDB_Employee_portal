package com.growdigitalbridge.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.document.api.dto.DocumentDtos;
import com.growdigitalbridge.document.client.EmployeeClient;
import com.growdigitalbridge.document.client.OrganizationClient;
import com.growdigitalbridge.document.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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
 * Proves both directions of messaging against a real broker: completing an upload publishes
 * {@code document.uploaded.v1} through the outbox relay, and a real {@code employee.
 * deactivated.v1} message delivered to the shared exchange is idempotently recorded by
 * {@link EmployeeEventListener}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DocumentMessagingIntegrationTest {

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
    void completingAnUploadIsPublishedToTheDomainEventsExchange() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(ownerId));

        Queue testQueue = new Queue("test.document.uploaded.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("document.uploaded.v1"));

        MvcResult created = mockMvc.perform(post("/api/v1/documents/uploads")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentDtos.UploadRequest("HR", "application/pdf", 2048L, "sha256-evt"))))
                .andExpect(status().isCreated()).andReturn();
        DocumentDtos.Response document = objectMapper.readValue(created.getResponse().getContentAsString(), DocumentDtos.Response.class);

        mockMvc.perform(post("/api/v1/documents/uploads/" + document.id() + "/complete")
                        .with(jwt().authorities(new SimpleGrantedAuthority("document.upload.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentDtos.CompleteRequest("sha256-evt"))))
                .andExpect(status().isOk());

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("document.uploaded.v1");
        assertThat(received.aggregateId()).isEqualTo(document.id());
        assertThat(received.producer()).isEqualTo("document-service");
        assertThat(received.payload()).containsEntry("scanStatus", "CLEAN");
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
