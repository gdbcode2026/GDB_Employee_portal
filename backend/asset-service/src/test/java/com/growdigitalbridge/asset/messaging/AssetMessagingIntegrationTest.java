package com.growdigitalbridge.asset.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.asset.api.dto.AssetAssignmentDtos;
import com.growdigitalbridge.asset.api.dto.AssetDtos;
import com.growdigitalbridge.asset.api.dto.AssetRequestDtos;
import com.growdigitalbridge.asset.client.EmployeeClient;
import com.growdigitalbridge.asset.domain.AssetRequestStatus;
import com.growdigitalbridge.asset.repository.AssetRequestRepository;
import com.growdigitalbridge.asset.repository.ProcessedEventRepository;
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
 * Proves all three documented messaging directions against a real broker: assigning an asset
 * publishes {@code asset.assigned.v1} through the outbox relay; a real {@code
 * employee.deactivated.v1} message is idempotently recorded (see {@link AssetEventListener}'s
 * Javadoc for why no further action is taken); and a real {@code workflow.completed.v1}
 * message with {@code subjectType=ASSET_REQUEST} applies the terminal outcome to the matching
 * AssetRequest - the only Asset<->Workflow integration path this service uses.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AssetMessagingIntegrationTest {

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
    @Autowired private AssetRequestRepository assetRequestRepository;

    @MockitoBean
    private EmployeeClient employeeClient;

    @Test
    void assigningAnAssetIsPublishedToTheDomainEventsExchange() throws Exception {
        MvcResult assetResult = mockMvc.perform(post("/api/v1/assets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetDtos.CreateRequest("EVT-1", "Laptop", null))))
                .andExpect(status().isCreated()).andReturn();
        AssetDtos.Response asset = objectMapper.readValue(assetResult.getResponse().getContentAsString(), AssetDtos.Response.class);
        UUID employeeRef = UUID.randomUUID();

        Queue testQueue = new Queue("test.asset.assigned.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("asset.assigned.v1"));

        mockMvc.perform(post("/api/v1/assets/" + asset.id() + "/assignments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.assign")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignmentDtos.CreateRequest(employeeRef))))
                .andExpect(status().isCreated());

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("asset.assigned.v1");
        assertThat(received.aggregateId()).isEqualTo(asset.id());
        assertThat(received.producer()).isEqualTo("asset-service");
        assertThat(received.payload()).containsEntry("employeeRef", employeeRef.toString());
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

    @Test
    void workflowCompletedEventAppliesTheOutcomeToTheMatchingAssetRequest() throws Exception {
        UUID self = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(self));

        MvcResult requestResult = mockMvc.perform(post("/api/v1/asset-requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("asset.request.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetRequestDtos.CreateRequest("Laptop", "New joiner"))))
                .andExpect(status().isCreated()).andReturn();
        AssetRequestDtos.Response created = objectMapper.readValue(requestResult.getResponse().getContentAsString(), AssetRequestDtos.Response.class);

        UUID workflowId = UUID.randomUUID();
        DomainEvent event = new DomainEvent(UUID.randomUUID(), "workflow.completed.v1", 1, Instant.now(), UUID.randomUUID(),
                "workflow-service", workflowId, Map.of(
                        "workflowId", workflowId.toString(),
                        "subjectType", "ASSET_REQUEST",
                        "subjectRef", created.id().toString(),
                        "outcome", "APPROVED",
                        "decisionTime", Instant.now().toString()));

        rabbitTemplate.convertAndSend("gdb.domain.events", "workflow.completed.v1", event);

        assertThat(awaitStatus(created.id(), AssetRequestStatus.APPROVED)).isTrue();
        assertThat(assetRequestRepository.findById(created.id()).orElseThrow().getWorkflowRef()).isEqualTo(workflowId);
    }

    private boolean awaitStatus(UUID assetRequestId, AssetRequestStatus expected) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            var current = assetRequestRepository.findById(assetRequestId);
            if (current.isPresent() && current.get().getStatus() == expected) {
                return true;
            }
            Thread.sleep(300);
        }
        return false;
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
