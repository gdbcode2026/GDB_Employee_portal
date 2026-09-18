package com.growdigitalbridge.project.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.project.api.dto.ProjectDtos;
import com.growdigitalbridge.project.client.EmployeeClient;
import com.growdigitalbridge.project.client.OrganizationClient;
import com.growdigitalbridge.platform.common.event.DomainEvent;
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
 * Proves the outbox relay actually publishes to a real broker: a real PostgreSQL and a real
 * RabbitMQ, a project created over the real HTTP/security stack, and a temporary queue bound
 * to the production exchange/routing key receiving the resulting well-formed envelope.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ProjectMessagingIntegrationTest {

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

    @MockitoBean
    private EmployeeClient employeeClient;

    @MockitoBean
    private OrganizationClient organizationClient;

    @Test
    void creatingAProjectIsPublishedToTheDomainEventsExchange() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(ownerId));

        Queue testQueue = new Queue("test.project.created.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("project.created.v1"));

        MvcResult created = mockMvc.perform(post("/api/v1/projects")
                        .with(jwt().authorities(new SimpleGrantedAuthority("project.manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectDtos.CreateRequest("PRJ-EVT-1", "Messaging Test Project"))))
                .andExpect(status().isCreated()).andReturn();
        ProjectDtos.Response project = objectMapper.readValue(created.getResponse().getContentAsString(), ProjectDtos.Response.class);

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("project.created.v1");
        assertThat(received.aggregateId()).isEqualTo(project.id());
        assertThat(received.producer()).isEqualTo("project-service");
        assertThat(received.payload()).containsEntry("ownerId", ownerId.toString());
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
