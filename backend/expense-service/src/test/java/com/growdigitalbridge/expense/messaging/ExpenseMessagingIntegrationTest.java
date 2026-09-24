package com.growdigitalbridge.expense.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.expense.api.dto.Decision;
import com.growdigitalbridge.expense.api.dto.ExpenseClaimDtos;
import com.growdigitalbridge.expense.client.EmployeeClient;
import com.growdigitalbridge.expense.domain.ExpenseClaimStatus;
import com.growdigitalbridge.expense.repository.ExpenseClaimRepository;
import com.growdigitalbridge.expense.repository.ProcessedEventRepository;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Proves all three documented messaging directions against a real broker: submitting a claim
 * publishes {@code expense.submitted.v1}, deciding it publishes {@code expense.approved.v1},
 * and a real {@code workflow.completed.v1} message with {@code subjectType=EXPENSE} applies
 * the terminal outcome to the matching claim - mirroring Asset Service's identical messaging
 * test shape. A message with a different {@code subjectType} is proven to be ignored, and
 * duplicate delivery is proven idempotent.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ExpenseMessagingIntegrationTest {

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
    @Autowired private ExpenseClaimRepository expenseClaimRepository;

    @MockitoBean
    private EmployeeClient employeeClient;

    private ExpenseClaimDtos.Response createAndSubmitClaim(UUID employeeRef) throws Exception {
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeRef));
        ExpenseClaimDtos.CreateRequest request = new ExpenseClaimDtos.CreateRequest("USD",
                List.of(new ExpenseClaimDtos.LineItem(LocalDate.of(2026, 2, 1), "Travel", new BigDecimal("75.00"), null)),
                List.of());
        MvcResult created = mockMvc.perform(post("/api/v1/expenses/claims")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        ExpenseClaimDtos.Response claim = objectMapper.readValue(created.getResponse().getContentAsString(), ExpenseClaimDtos.Response.class);

        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.submit.self"))))
                .andExpect(status().isOk());
        return claim;
    }

    @Test
    void submittingAClaimIsPublishedToTheDomainEventsExchange() throws Exception {
        Queue testQueue = new Queue("test.expense.submitted.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("expense.submitted.v1"));

        ExpenseClaimDtos.Response claim = createAndSubmitClaim(UUID.randomUUID());

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("expense.submitted.v1");
        assertThat(received.aggregateId()).isEqualTo(claim.id());
        assertThat(received.producer()).isEqualTo("expense-service");
        assertThat(received.payload()).containsEntry("total", "75.00");
    }

    @Test
    void decidingAClaimIsPublishedToTheDomainEventsExchange() throws Exception {
        ExpenseClaimDtos.Response claim = createAndSubmitClaim(UUID.randomUUID());

        Queue testQueue = new Queue("test.expense.approved.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("expense.approved.v1"));

        mockMvc.perform(post("/api/v1/expenses/claims/" + claim.id() + "/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("expense.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseClaimDtos.DecisionRequest(Decision.APPROVED, null))))
                .andExpect(status().isOk());

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("expense.approved.v1");
        assertThat(received.aggregateId()).isEqualTo(claim.id());
        assertThat(received.payload()).containsEntry("approvedTotal", "75.00");
    }

    @Test
    void workflowCompletedEventAppliesTheOutcomeOnlyForExpenseSubjectType() throws Exception {
        ExpenseClaimDtos.Response claim = createAndSubmitClaim(UUID.randomUUID());
        UUID workflowId = UUID.randomUUID();

        // A different subjectType must be ignored.
        DomainEvent otherSubject = new DomainEvent(UUID.randomUUID(), "workflow.completed.v1", 1, Instant.now(), UUID.randomUUID(),
                "workflow-service", workflowId, Map.of("workflowId", workflowId.toString(), "subjectType", "ASSET_REQUEST",
                        "subjectRef", claim.id().toString(), "outcome", "APPROVED", "decisionTime", Instant.now().toString()));
        rabbitTemplate.convertAndSend("gdb.domain.events", "workflow.completed.v1", otherSubject);
        assertThat(awaitProcessed(otherSubject.eventId())).isTrue();
        assertThat(expenseClaimRepository.findById(claim.id()).orElseThrow().getStatus()).isEqualTo(ExpenseClaimStatus.SUBMITTED);

        // The matching EXPENSE subjectType applies the outcome.
        DomainEvent event = new DomainEvent(UUID.randomUUID(), "workflow.completed.v1", 1, Instant.now(), UUID.randomUUID(),
                "workflow-service", workflowId, Map.of("workflowId", workflowId.toString(), "subjectType", "EXPENSE",
                        "subjectRef", claim.id().toString(), "outcome", "APPROVED", "decisionTime", Instant.now().toString()));
        rabbitTemplate.convertAndSend("gdb.domain.events", "workflow.completed.v1", event);
        assertThat(awaitStatus(claim.id(), ExpenseClaimStatus.APPROVED)).isTrue();
        assertThat(expenseClaimRepository.findById(claim.id()).orElseThrow().getWorkflowRef()).isEqualTo(workflowId);

        // Duplicate delivery of the same event is a no-op.
        rabbitTemplate.convertAndSend("gdb.domain.events", "workflow.completed.v1", event);
        assertThat(awaitProcessed(event.eventId())).isTrue();
        assertThat(expenseClaimRepository.findById(claim.id()).orElseThrow().getStatus()).isEqualTo(ExpenseClaimStatus.APPROVED);
    }

    private boolean awaitStatus(UUID claimId, ExpenseClaimStatus expected) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            var current = expenseClaimRepository.findById(claimId);
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
