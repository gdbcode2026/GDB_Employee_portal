package com.growdigitalbridge.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.api.dto.PayrollPeriodDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.client.DocumentServiceClient;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.OutboxEvent;
import com.growdigitalbridge.payroll.domain.PayFrequency;
import com.growdigitalbridge.payroll.domain.Payslip;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import com.growdigitalbridge.payroll.repository.OutboxEventRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import com.growdigitalbridge.payroll.repository.PayslipRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payroll Adjustment Run support (PAYROLL_REQUIREMENTS.md Section K): {@code POST
 * /payroll/runs/{id}/adjustments} against real PostgreSQL/RabbitMQ, with Employee Service and
 * Document Service mocked at their client boundary exactly like {@code PayslipIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PayrollAdjustmentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private EmployeeCompensationRepository compensationRepository;
    @Autowired private CompensationComponentRepository compensationComponentRepository;
    @Autowired private PayslipRepository payslipRepository;
    @Autowired private PayrollRunLineRepository lineRepository;

    @MockitoBean private EmployeeClient employeeClient;
    @MockitoBean private DocumentServiceClient documentServiceClient;

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

    private PayrollRunDtos.Response processSubmitApproveFinalize(UUID runId, String maker, String checker) throws Exception {
        mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject(checker)).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/finalize")
                        .with(jwt().jwt(jwt -> jwt.subject(checker)).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
    }

    /** TEST DATA - synthetic amounts only, never real salary/tax figures. */
    private void demoCompensation(UUID employeeRef, LocalDate effectiveFrom) {
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
    }

    private void stubDocumentServiceUpload() {
        when(documentServiceClient.createWorkloadUpload(any(), eq("PAYSLIP"), eq("application/pdf"), anyLong(), anyString()))
                .thenAnswer(invocation -> new DocumentServiceClient.UploadResponse(UUID.randomUUID(),
                        invocation.getArgument(0), "PAYSLIP", "PENDING_SCAN", Instant.now(), Instant.now()));
    }

    private PayrollRunDtos.Response finalizedOriginalRun(int month, UUID employeeRef, String maker, String checker) throws Exception {
        demoCompensation(employeeRef, LocalDate.of(Year.now().getValue() + 4, 1, 1));
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeRef));
        stubDocumentServiceUpload();

        UUID periodId = createPeriod(Year.now().getValue() + 4, month);
        PayrollRunDtos.Response run = createRun(periodId, maker);
        return processSubmitApproveFinalize(run.id(), maker, checker);
    }

    private PayrollRunDtos.Response createAdjustment(UUID originalRunId, String actor) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/runs/" + originalRunId + "/adjustments")
                        .with(jwt().jwt(jwt -> jwt.subject(actor)).authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
    }

    @Test
    void creatingAnAdjustmentAgainstAFinalizedRunSucceedsAndCarriesTheCorrectsRunId() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedOriginalRun(1, employeeRef, "maker-1", "checker-1");

        PayrollRunDtos.Response adjustment = createAdjustment(original.id(), "maker-2");

        assertThat(adjustment.runType().name()).isEqualTo("ADJUSTMENT");
        assertThat(adjustment.correctsRunId()).isEqualTo(original.id());
        assertThat(adjustment.status().name()).isEqualTo("DRAFT");
        assertThat(adjustment.periodId()).isEqualTo(original.periodId());
        assertThat(adjustment.employeeCount()).isEqualTo(1);
        assertThat(adjustment.id()).isNotEqualTo(original.id());
    }

    @Test
    void anAdjustmentCanOnlyBeCreatedAgainstAFinalizedOriginalRun() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        demoCompensation(employeeRef, LocalDate.of(Year.now().getValue() + 4, 1, 1));
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(Year.now().getValue() + 4, 2);
        PayrollRunDtos.Response draftRun = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + draftRun.id() + "/adjustments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void creatingAnAdjustmentDoesNotMutateTheOriginalRun() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedOriginalRun(3, employeeRef, "maker-1", "checker-1");

        createAdjustment(original.id(), "maker-2");

        MvcResult refetched = mockMvc.perform(get("/api/v1/payroll/runs/" + original.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk()).andReturn();
        PayrollRunDtos.Response reread = objectMapper.readValue(refetched.getResponse().getContentAsString(), PayrollRunDtos.Response.class);

        assertThat(reread.status().name()).isEqualTo("FINALIZED");
        assertThat(reread.approvedBy()).isEqualTo(original.approvedBy());
        assertThat(reread.finalizedAt()).isEqualTo(original.finalizedAt());
        assertThat(reread.correctsRunId()).isNull();
        assertThat(reread.runType().name()).isEqualTo("REGULAR");
    }

    @Test
    void creatingAnAdjustmentRequiresPayrollProcessAuthority() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedOriginalRun(4, employeeRef, "maker-1", "checker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + original.id() + "/adjustments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void repeatedAdjustmentCreationWhileOneIsStillInProgressIsRejectedAsAConflict() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedOriginalRun(5, employeeRef, "maker-1", "checker-1");

        createAdjustment(original.id(), "maker-2");

        mockMvc.perform(post("/api/v1/payroll/runs/" + original.id() + "/adjustments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isConflict());
    }

    @Test
    void aSecondAdjustmentCanBeCreatedOnceTheFirstHasReachedFinalized() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedOriginalRun(6, employeeRef, "maker-1", "checker-1");

        PayrollRunDtos.Response firstAdjustment = createAdjustment(original.id(), "maker-2");
        processSubmitApproveFinalize(firstAdjustment.id(), "maker-2", "checker-2");

        PayrollRunDtos.Response secondAdjustment = createAdjustment(original.id(), "maker-3");

        assertThat(secondAdjustment.correctsRunId()).isEqualTo(original.id());
        assertThat(secondAdjustment.id()).isNotEqualTo(firstAdjustment.id());
    }

    @Test
    void adjustmentSelfApprovalIsRejectedEvenWhenTheCreatorAlsoHoldsApprove() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedOriginalRun(7, employeeRef, "maker-1", "checker-1");
        PayrollRunDtos.Response adjustment = createAdjustment(original.id(), "maker-2");

        mockMvc.perform(post("/api/v1/payroll/runs/" + adjustment.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + adjustment.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payroll/runs/" + adjustment.id() + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject("maker-2"))
                                .authorities(new SimpleGrantedAuthority("payroll.process"), new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payroll/runs/" + adjustment.id() + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-2")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk());
    }

    @Test
    void adjustmentCalculationReusesTheEngineAndSupportsASignedNegativeLine() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        // A clawback component (negative EARNING) alongside the normal ones - the engine applies
        // no positivity constraint and no netting/accounting policy; it simply sums what is
        // configured, exactly like any other run (Section K's signed-line model).
        EmployeeCompensation compensation = new EmployeeCompensation(UUID.randomUUID(), employeeRef, "INR",
                PayFrequency.MONTHLY, LocalDate.of(Year.now().getValue() + 4, 1, 1), null, "hr-1", Instant.now());
        compensationRepository.save(compensation);
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), compensation.getId(), "BASIC_SALARY",
                CompensationComponentType.EARNING, new BigDecimal("50000.00"), null, "hr-1", Instant.now()));
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), compensation.getId(), "OTHER_EARNING",
                CompensationComponentType.EARNING, new BigDecimal("-3000.00"), null, "hr-1", Instant.now()));
        compensationComponentRepository.save(new CompensationComponent(UUID.randomUUID(), compensation.getId(), "PF",
                CompensationComponentType.DEDUCTION, new BigDecimal("6000.00"), null, "hr-1", Instant.now()));

        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeRef));
        stubDocumentServiceUpload();
        UUID periodId = createPeriod(Year.now().getValue() + 4, 8);
        PayrollRunDtos.Response original = createRun(periodId, "maker-1");
        processSubmitApproveFinalize(original.id(), "maker-1", "checker-1");

        PayrollRunDtos.Response adjustment = createAdjustment(original.id(), "maker-2");
        processSubmitApproveFinalize(adjustment.id(), "maker-2", "checker-2");

        PayrollRunLine adjustmentLine = lineRepository.findByRunIdAndEmployeeRef(adjustment.id(), employeeRef).orElseThrow();
        assertThat(adjustmentLine.getGrossPay()).isEqualByComparingTo("47000.00");
        assertThat(adjustmentLine.getTotalDeductions()).isEqualByComparingTo("6000.00");
        assertThat(adjustmentLine.getNetPay()).isEqualByComparingTo("41000.00");
        assertThat(adjustmentLine.getComponentBreakdown()).contains("OTHER_EARNING");
    }

    @Test
    void finalizingAnAdjustmentRunGeneratesItsOwnPayslipAndLeavesTheOriginalPayslipUnchanged() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedOriginalRun(9, employeeRef, "maker-1", "checker-1");
        Payslip originalPayslip = payslipRepository.findByRunIdAndEmployeeRef(original.id(), employeeRef).orElseThrow();

        PayrollRunDtos.Response adjustment = createAdjustment(original.id(), "maker-2");
        processSubmitApproveFinalize(adjustment.id(), "maker-2", "checker-2");

        Optional<Payslip> adjustmentPayslip = payslipRepository.findByRunIdAndEmployeeRef(adjustment.id(), employeeRef);
        assertThat(adjustmentPayslip).isPresent();
        assertThat(adjustmentPayslip.get().getId()).isNotEqualTo(originalPayslip.getId());
        assertThat(adjustmentPayslip.get().getDocumentRef()).isNotEqualTo(originalPayslip.getDocumentRef());

        // The original payslip row is byte-for-byte unchanged.
        Payslip originalPayslipReread = payslipRepository.findByRunIdAndEmployeeRef(original.id(), employeeRef).orElseThrow();
        assertThat(originalPayslipReread.getId()).isEqualTo(originalPayslip.getId());
        assertThat(originalPayslipReread.getDocumentRef()).isEqualTo(originalPayslip.getDocumentRef());
        assertThat(originalPayslipReread.getGeneratedAt()).isEqualTo(originalPayslip.getGeneratedAt());

        // Document Service was called once per payslip (original + adjustment).
        verify(documentServiceClient, org.mockito.Mockito.times(2))
                .createWorkloadUpload(eq(employeeRef), eq("PAYSLIP"), eq("application/pdf"), anyLong(), anyString());

        List<OutboxEvent> payslipEvents = outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType().equals("payslip.generated.v1")
                        && (event.getAggregateId().equals(originalPayslip.getId()) || event.getAggregateId().equals(adjustmentPayslip.get().getId())))
                .toList();
        assertThat(payslipEvents).hasSize(2);
        for (OutboxEvent event : payslipEvents) {
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            assertThat(payload.keySet()).containsExactlyInAnyOrder("payslipId", "employeeId", "runId", "periodId", "generatedAt");
        }
    }
}
