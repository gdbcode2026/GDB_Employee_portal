package com.growdigitalbridge.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.api.dto.PayrollPeriodDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayFrequency;
import com.growdigitalbridge.payroll.domain.OutboxEvent;
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import com.growdigitalbridge.payroll.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Flyway migration and JPA mappings against genuine PostgreSQL, and the real
 * security filter chain, while mocking only Employee Service (a separate service, not something
 * this test should stand up).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PayrollIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EmployeeCompensationRepository compensationRepository;

    @Autowired
    private CompensationComponentRepository compensationComponentRepository;

    @MockitoBean
    private EmployeeClient employeeClient;

    private UUID createPeriod(int year, int month) throws Exception {
        var request = new PayrollPeriodDtos.CreateRequest(year, month, null);
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/periods")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PayrollPeriodDtos.Response.class).id();
    }

    private PayrollRunDtos.Response createRun(UUID periodId, String actor) throws Exception {
        var request = new PayrollRunDtos.CreateRequest(periodId);
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/runs")
                        .with(jwt().jwt(jwt -> jwt.subject(actor)).authorities(new SimpleGrantedAuthority("payroll.process")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
    }

    @Test
    void periodLifecycleCreateListRetrieveAndRejectDuplicate() throws Exception {
        int year = Year.now().getValue() + 1;
        UUID periodId = createPeriod(year, 6);

        mockMvc.perform(get("/api/v1/payroll/periods/" + periodId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(year))
                .andExpect(jsonPath("$.month").value(6))
                .andExpect(jsonPath("$.startDate").value(year + "-06-01"))
                .andExpect(jsonPath("$.endDate").value(year + "-06-30"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/payroll/periods")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());

        // Duplicate year/month is rejected as a conflict.
        var duplicate = new PayrollPeriodDtos.CreateRequest(year, 6, null);
        mockMvc.perform(post("/api/v1/payroll/periods")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void periodCreationRejectsAnInvalidMonth() throws Exception {
        var request = new PayrollPeriodDtos.CreateRequest(Year.now().getValue() + 1, 13, null);
        mockMvc.perform(post("/api/v1/payroll/periods")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void runCreationSnapshotsEmployeesAndIsUnaffectedByLaterEmployeeClientChanges() throws Exception {
        UUID periodId = createPeriod(Year.now().getValue() + 1, 1);
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeA, employeeB));

        PayrollRunDtos.Response run = createRun(periodId, "maker-1");
        assertThat(run.employeeCount()).isEqualTo(2);
        assertThat(run.status().name()).isEqualTo("DRAFT");

        // Employee Service now reports a different population; the already-created run must not change.
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(UUID.randomUUID()));

        MvcResult refetched = mockMvc.perform(get("/api/v1/payroll/runs/" + run.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk()).andReturn();
        PayrollRunDtos.Response reread = objectMapper.readValue(refetched.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
        assertThat(reread.employeeCount()).isEqualTo(2);
    }

    @Test
    void duplicateRunCreationForTheSamePeriodIsRejectedAsAConflict() throws Exception {
        UUID periodId = createPeriod(Year.now().getValue() + 1, 2);
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(UUID.randomUUID()));

        createRun(periodId, "maker-1");

        var second = new PayrollRunDtos.CreateRequest(periodId);
        mockMvc.perform(post("/api/v1/payroll/runs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict());
    }

    @Test
    void fullLifecycleProcessSubmitApproveFinalizeEmitsPayrollProcessed() throws Exception {
        UUID periodId = createPeriod(Year.now().getValue() + 1, 3);
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"));

        // Duplicate processing from CALCULATED is rejected (idempotency-by-guard).
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value("checker-1"));

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/finalize")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        List<OutboxEvent> events = outboxEventRepository.findAll();
        OutboxEvent published = events.stream()
                .filter(event -> event.getEventType().equals("payroll.processed.v1") && event.getAggregateId().equals(run.id()))
                .findFirst().orElseThrow(() -> new AssertionError("No payroll.processed.v1 outbox event was written for run " + run.id()));
        Map<String, Object> payload = objectMapper.readValue(published.getPayload(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        assertThat(payload).containsEntry("payrollRunId", run.id().toString());
        assertThat(payload).containsEntry("periodId", periodId.toString());
        assertThat(((Number) payload.get("employeeCount")).intValue()).isEqualTo(3);

        // Terminal: finalize cannot be repeated.
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/finalize")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectionReturnsTheRunToProcessingForCorrection() throws Exception {
        UUID periodId = createPeriod(Year.now().getValue() + 1, 4);
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(UUID.randomUUID()));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/reject")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // A rejected run can be processed again (re-enters the cycle).
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"));
    }

    @Test
    void selfApprovalIsRejectedEvenWhenTheInitiatorAlsoHoldsApprove() throws Exception {
        UUID periodId = createPeriod(Year.now().getValue() + 1, 5);
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(UUID.randomUUID()));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());

        // "maker-1" holds BOTH permissions but initiated this run - approval must still be denied.
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject("maker-1"))
                                .authorities(new SimpleGrantedAuthority("payroll.process"), new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isForbidden());

        // A different identity holding payroll.approve succeeds.
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/runs")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingPermissionIsRejectedAsForbidden() throws Exception {
        UUID periodId = createPeriod(Year.now().getValue() + 1, 7);

        // payroll.read.all cannot create a period or a run (maker permission required).
        var createPeriod = new PayrollPeriodDtos.CreateRequest(Year.now().getValue() + 1, 8, null);
        mockMvc.perform(post("/api/v1/payroll/periods")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPeriod)))
                .andExpect(status().isForbidden());

        var createRun = new PayrollRunDtos.CreateRequest(periodId);
        mockMvc.perform(post("/api/v1/payroll/runs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRun)))
                .andExpect(status().isForbidden());

        // payroll.process cannot approve (checker permission required).
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(UUID.randomUUID()));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/approve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void effectiveDateCompensationResolvesTheRecordCoveringTheGivenDate() {
        UUID employeeRef = UUID.randomUUID();
        Instant now = Instant.now();
        EmployeeCompensation historical = new EmployeeCompensation(UUID.randomUUID(), employeeRef, "INR", PayFrequency.MONTHLY,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "hr-1", now);
        EmployeeCompensation current = new EmployeeCompensation(UUID.randomUUID(), employeeRef, "INR", PayFrequency.MONTHLY,
                LocalDate.of(2025, 1, 1), null, "hr-1", now);
        compensationRepository.save(historical);
        compensationRepository.save(current);
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), current.getId(), "BASIC",
                CompensationComponentType.EARNING, new BigDecimal("1.00"), null, "hr-1", now));

        assertThat(compensationRepository.findEffectiveForEmployee(employeeRef, LocalDate.of(2024, 6, 15)))
                .map(EmployeeCompensation::getId).contains(historical.getId());
        assertThat(compensationRepository.findEffectiveForEmployee(employeeRef, LocalDate.of(2025, 6, 15)))
                .map(EmployeeCompensation::getId).contains(current.getId());
        assertThat(compensationRepository.findEffectiveForEmployee(employeeRef, LocalDate.of(2023, 1, 1)))
                .isEmpty();
        assertThat(compensationComponentRepository.findByCompensationId(current.getId())).hasSize(1);
    }
}
