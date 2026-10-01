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
import com.growdigitalbridge.payroll.domain.PayrollAttendanceInput;
import com.growdigitalbridge.payroll.domain.PayrollException;
import com.growdigitalbridge.payroll.domain.PayrollExceptionReason;
import com.growdigitalbridge.payroll.domain.PayrollLeaveInput;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import com.growdigitalbridge.payroll.repository.OutboxEventRepository;
import com.growdigitalbridge.payroll.repository.PayrollAttendanceInputRepository;
import com.growdigitalbridge.payroll.repository.PayrollExceptionRepository;
import com.growdigitalbridge.payroll.repository.PayrollLeaveInputRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
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
import org.testcontainers.containers.RabbitMQContainer;
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

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

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

    @Autowired
    private PayrollRunLineRepository lineRepository;

    @Autowired
    private PayrollExceptionRepository exceptionRepository;

    @Autowired
    private PayrollAttendanceInputRepository attendanceInputRepository;

    @Autowired
    private PayrollLeaveInputRepository leaveInputRepository;

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

        // Re-finalizing an already-FINALIZED run is the documented idempotent retry mechanism
        // (payslip generation retry) - it succeeds again rather than erroring, and does not
        // re-mutate the run or duplicate the payroll.processed.v1 event.
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/finalize")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));
        long processedEventCount = outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType().equals("payroll.processed.v1") && event.getAggregateId().equals(run.id()))
                .count();
        assertThat(processedEventCount).isEqualTo(1);
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

    /** TEST DATA — synthetic amounts only, never real salary/tax figures. */
    private EmployeeCompensation demoCompensation(UUID employeeRef, LocalDate effectiveFrom) {
        EmployeeCompensation compensation = new EmployeeCompensation(UUID.randomUUID(), employeeRef, "INR",
                PayFrequency.MONTHLY, effectiveFrom, null, "hr-1", Instant.now());
        compensationRepository.save(compensation);
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), compensation.getId(), "BASIC_SALARY",
                CompensationComponentType.EARNING, new BigDecimal("50000.00"), null, "hr-1", Instant.now()));
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), compensation.getId(), "HRA",
                CompensationComponentType.EARNING, new BigDecimal("20000.00"), null, "hr-1", Instant.now()));
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), compensation.getId(), "PF",
                CompensationComponentType.DEDUCTION, new BigDecimal("6000.00"), null, "hr-1", Instant.now()));
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), compensation.getId(), "EMPLOYER_PF",
                CompensationComponentType.EMPLOYER_CONTRIBUTION, new BigDecimal("6000.00"), null, "hr-1", Instant.now()));
        return compensation;
    }

    @Test
    void earningsDeductionsAndEmployerContributionsAreAggregatedIntoALine() throws Exception {
        int year = Year.now().getValue() + 2;
        UUID employeeRef = UUID.randomUUID();
        demoCompensation(employeeRef, LocalDate.of(year, 1, 1));
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeRef));

        UUID periodId = createPeriod(year, 1);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.lineCount").value(1))
                // No EmployeeStatutoryProfile was created for this employee, so the calculation
                // engine's statutory-profile completeness check (Compensation Management task)
                // flags exactly one MISSING_STATUTORY_PROFILE exception alongside the line - it
                // does not skip or alter the line itself.
                .andExpect(jsonPath("$.exceptionCount").value(1));

        List<PayrollRunLine> lines = lineRepository.findByRunId(run.id());
        assertThat(lines).hasSize(1);
        PayrollRunLine line = lines.get(0);
        assertThat(line.getGrossPay()).isEqualByComparingTo("70000.00");
        assertThat(line.getTotalDeductions()).isEqualByComparingTo("6000.00");
        assertThat(line.getTotalEmployerContributions()).isEqualByComparingTo("6000.00");
        assertThat(line.getNetPay()).isEqualByComparingTo("64000.00");
        assertThat(line.getComponentBreakdown()).contains("BASIC_SALARY", "HRA", "PF", "EMPLOYER_PF");

        List<PayrollException> exceptions = exceptionRepository.findByRunId(run.id());
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0).getEmployeeRef()).isEqualTo(employeeRef);
        assertThat(exceptions.get(0).getReason()).isEqualTo(PayrollExceptionReason.MISSING_STATUTORY_PROFILE);
    }

    @Test
    void missingCompensationProducesAPayrollExceptionWithoutBlockingTheRun() throws Exception {
        int year = Year.now().getValue() + 2;
        UUID employeeWithCompensation = UUID.randomUUID();
        UUID employeeWithoutCompensation = UUID.randomUUID();
        demoCompensation(employeeWithCompensation, LocalDate.of(year, 2, 1));
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeWithCompensation, employeeWithoutCompensation));

        UUID periodId = createPeriod(year, 2);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.lineCount").value(1))
                // employeeWithoutCompensation -> NO_EFFECTIVE_COMPENSATION (no line);
                // employeeWithCompensation -> MISSING_STATUTORY_PROFILE (alongside its own line,
                // since no EmployeeStatutoryProfile was created for it either).
                .andExpect(jsonPath("$.exceptionCount").value(2));

        List<PayrollException> exceptions = exceptionRepository.findByRunId(run.id());
        assertThat(exceptions).hasSize(2);
        assertThat(exceptions).anySatisfy(exception -> {
            assertThat(exception.getEmployeeRef()).isEqualTo(employeeWithoutCompensation);
            assertThat(exception.getReason()).isEqualTo(PayrollExceptionReason.NO_EFFECTIVE_COMPENSATION);
        });
        assertThat(exceptions).anySatisfy(exception -> {
            assertThat(exception.getEmployeeRef()).isEqualTo(employeeWithCompensation);
            assertThat(exception.getReason()).isEqualTo(PayrollExceptionReason.MISSING_STATUTORY_PROFILE);
        });
    }

    @Test
    void noOpProrationLeavesEarningsUnchangedRegardlessOfAttendanceOrLeaveInput() throws Exception {
        int year = Year.now().getValue() + 2;
        UUID employeeRef = UUID.randomUUID();
        LocalDate periodStart = LocalDate.of(year, 3, 1);
        demoCompensation(employeeRef, periodStart);
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeRef));

        // Real attendance/leave input snapshots, exactly as the messaging listeners would persist them.
        attendanceInputRepository.save(new PayrollAttendanceInput(UUID.randomUUID(), employeeRef, periodStart,
                UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        leaveInputRepository.save(new PayrollLeaveInput(UUID.randomUUID(), employeeRef, UUID.randomUUID(),
                new BigDecimal("3"), UUID.randomUUID(), Instant.now()));

        UUID periodId = createPeriod(year, 3);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"));

        PayrollRunLine line = lineRepository.findByRunId(run.id()).get(0);
        assertThat(line.getGrossPay()).isEqualByComparingTo("70000.00");
        List<Map<String, Object>> breakdown = objectMapper.readValue(line.getComponentBreakdown(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
        assertThat(breakdown).allSatisfy(entry -> assertThat(((Number) entry.get("prorationAdjustment")).intValue()).isZero());
    }

    @Test
    void calculationFailureRollsBackAndSafelyRecordsTheFailureThenAllowsReprocessing() throws Exception {
        int year = Year.now().getValue() + 2;
        UUID employeeRef = UUID.randomUUID();
        LocalDate periodStart = LocalDate.of(year, 4, 1);

        // Two overlapping effective compensation records for the same employee/date is an
        // ambiguous, invalid data state the resolver cannot handle - a genuine engine failure,
        // not a mocked one, that must roll back completely.
        EmployeeCompensation first = demoCompensation(employeeRef, periodStart);
        EmployeeCompensation second = demoCompensation(employeeRef, periodStart);
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeRef));

        UUID periodId = createPeriod(year, 4);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().is5xxServerError());

        MvcResult afterFailure = mockMvc.perform(get("/api/v1/payroll/runs/" + run.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk()).andReturn();
        PayrollRunDtos.Response reread = objectMapper.readValue(afterFailure.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
        assertThat(reread.status().name()).isEqualTo("CALCULATION_FAILED");
        assertThat(lineRepository.findByRunId(run.id())).isEmpty();

        // Fix the ambiguous data, then prove the failed run can be reprocessed cleanly.
        compensationComponentRepository.deleteAll(compensationComponentRepository.findByCompensationId(second.getId()));
        compensationRepository.deleteById(second.getId());

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.lineCount").value(1));
        assertThat(lineRepository.findByRunId(run.id())).hasSize(1);
    }
}
