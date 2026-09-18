package com.growdigitalbridge.leave.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.leave.api.dto.LeaveBalanceDtos;
import com.growdigitalbridge.leave.api.dto.LeaveRequestDtos;
import com.growdigitalbridge.leave.api.dto.LeaveTypeDtos;
import com.growdigitalbridge.leave.client.EmployeeClient;
import com.growdigitalbridge.leave.client.OrganizationClient;
import com.growdigitalbridge.platform.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the outbox relay actually publishes {@code leave.requested.v1} to a real broker,
 * mirroring Employee/Attendance Service's messaging integration test shape.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LeaveMessagingIntegrationTest {

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
    void submittingLeaveIsPublishedToTheDomainEventsExchange() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeId));

        MvcResult typesResult = mockMvc.perform(get("/api/v1/leave/types")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.read.self"))))
                .andExpect(status().isOk()).andReturn();
        List<LeaveTypeDtos.Response> types = objectMapper.readValue(typesResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, LeaveTypeDtos.Response.class));
        UUID leaveTypeId = types.get(0).id();

        mockMvc.perform(post("/api/v1/leave/balances")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.approve.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LeaveBalanceDtos.AllocateRequest(employeeId, leaveTypeId, Year.now().getValue(), new BigDecimal("10")))))
                .andExpect(status().isCreated());

        Queue testQueue = new Queue("test.leave.requested.v1", false, false, true);
        amqpAdmin.declareQueue(testQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(testQueue).to(domainEventsExchange).with("leave.requested.v1"));

        LocalDate start = LocalDate.now().plusDays(5);
        MvcResult created = mockMvc.perform(post("/api/v1/leave/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("leave.create.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LeaveRequestDtos.CreateRequest(leaveTypeId, start, start, "Appointment"))))
                .andExpect(status().isCreated()).andReturn();
        LeaveRequestDtos.Response leaveRequest = objectMapper.readValue(created.getResponse().getContentAsString(), LeaveRequestDtos.Response.class);

        DomainEvent received = awaitMessage(testQueue.getName());

        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo("leave.requested.v1");
        assertThat(received.aggregateId()).isEqualTo(leaveRequest.id());
        assertThat(received.producer()).isEqualTo("leave-service");
        assertThat(received.payload()).containsEntry("employeeId", employeeId.toString());
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
