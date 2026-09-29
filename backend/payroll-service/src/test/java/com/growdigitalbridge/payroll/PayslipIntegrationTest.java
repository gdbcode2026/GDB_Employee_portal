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
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import com.growdigitalbridge.payroll.repository.OutboxEventRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end payslip generation and access (items 1-14), against real PostgreSQL/RabbitMQ.
 * Employee Service and Document Service are mocked at their client boundary, exactly like {@code
 * PayrollIntegrationTest} mocks {@code EmployeeClient} - neither is a service this test should
 * stand up.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PayslipIntegrationTest {

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

    /** Sets up compensation for a brand-new employee, then finalizes one run for that employee/period. */
    private PayrollRunDtos.Response finalizedRunWithOnePayslip(int month, UUID employeeRef, String maker, String checker) throws Exception {
        demoCompensation(employeeRef, LocalDate.of(Year.now().getValue() + 3, 1, 1));
        return finalizeAnotherRunForTheSameEmployee(month, employeeRef, maker, checker);
    }

    /**
     * Finalizes an additional run/period for an employee whose compensation was already set up
     * (by an earlier call to {@link #finalizedRunWithOnePayslip}) - deliberately does NOT create a
     * second {@code EmployeeCompensation} record, since two open-ended records for the same
     * employee would overlap and make the calculation engine correctly reject the run as
     * ambiguous (the same real bug class {@code PayrollIntegrationTest} exercises deliberately).
     */
    private PayrollRunDtos.Response finalizeAnotherRunForTheSameEmployee(int month, UUID employeeRef, String maker, String checker) throws Exception {
        when(employeeClient.resolveActiveEmployeeRefs()).thenReturn(Set.of(employeeRef));
        stubDocumentServiceUpload();

        UUID periodId = createPeriod(Year.now().getValue() + 3, month);
        PayrollRunDtos.Response run = createRun(periodId, maker);

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/submit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject(checker)).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/finalize")
                        .with(jwt().jwt(jwt -> jwt.subject(checker)).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));
        return run;
    }

    @Test
    void finalizingAnApprovedRunGeneratesExactlyOnePayslipUploadsToDocumentServiceAndEmitsOneEventWithNoSalaryValues() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response run = finalizedRunWithOnePayslip(1, employeeRef, "maker-1", "checker-1");

        Optional<Payslip> payslip = payslipRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef);
        assertThat(payslip).isPresent();
        verify(documentServiceClient).createWorkloadUpload(eq(employeeRef), eq("PAYSLIP"), eq("application/pdf"), anyLong(), anyString());
        verify(documentServiceClient).completeUpload(eq(payslip.get().getDocumentRef()), anyString());

        List<OutboxEvent> events = outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType().equals("payslip.generated.v1") && event.getAggregateId().equals(payslip.get().getId()))
                .toList();
        assertThat(events).hasSize(1);
        Map<String, Object> payload = objectMapper.readValue(events.get(0).getPayload(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        assertThat(payload.keySet()).containsExactlyInAnyOrder("payslipId", "employeeId", "runId", "periodId", "generatedAt");
        assertThat(payload).containsEntry("payslipId", payslip.get().getId().toString());
        assertThat(payload).containsEntry("employeeId", employeeRef.toString());
        assertThat(payload).containsEntry("runId", run.id().toString());
    }

    @Test
    void retryingFinalizeOnAnAlreadyFinalizedRunDoesNotDuplicateThePayslipDocumentOrEvent() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response run = finalizedRunWithOnePayslip(2, employeeRef, "maker-1", "checker-1");

        // Idempotent retry: same endpoint, already-FINALIZED run.
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/finalize")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"));

        Payslip payslip = payslipRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        long eventsForThisPayslip = outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType().equals("payslip.generated.v1") && event.getAggregateId().equals(payslip.getId()))
                .count();
        assertThat(eventsForThisPayslip).isEqualTo(1);
        verify(documentServiceClient, times(1)).createWorkloadUpload(eq(employeeRef), any(), any(), anyLong(), any());
    }

    @Test
    void ownerCanListAndViewTheirOwnPayslipAndDownloadItViaTheSecureDocumentServiceFlow() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response run = finalizedRunWithOnePayslip(3, employeeRef, "maker-1", "checker-1");
        Payslip payslip = payslipRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeRef));

        mockMvc.perform(get("/api/v1/payroll/payslips/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(payslip.getId().toString()));

        mockMvc.perform(get("/api/v1/payroll/payslips/" + payslip.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPay").value(70000.00))
                .andExpect(jsonPath("$.netPay").value(64000.00))
                .andExpect(jsonPath("$.amountInWords").value("Sixty Four Thousand Rupees Only"));

        when(documentServiceClient.downloadOnBehalfOfCaller(payslip.getDocumentRef()))
                .thenReturn(new DocumentServiceClient.DownloadResponse(payslip.getDocumentRef(), "payslips/obj-key", "abc123", "application/pdf", 4096L));

        mockMvc.perform(get("/api/v1/payroll/payslips/" + payslip.getId() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(payslip.getDocumentRef().toString()))
                .andExpect(jsonPath("$.objectKey").value("payslips/obj-key"))
                .andExpect(jsonPath("$.checksum").value("abc123"));
    }

    @Test
    void aDifferentEmployeeHoldingOnlySelfAccessCannotViewSomeoneElsesPayslip() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response run = finalizedRunWithOnePayslip(4, employeeRef, "maker-1", "checker-1");
        Payslip payslip = payslipRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/payroll/payslips/" + payslip.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payroll/payslips/" + payslip.getId() + "/download")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrOrFinanceHoldingReadAllCanViewAnyEmployeesPayslipRegardlessOfSelf() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response run = finalizedRunWithOnePayslip(5, employeeRef, "maker-1", "checker-1");
        Payslip payslip = payslipRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();

        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/payroll/payslips/" + payslip.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.all"))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedAndUnauthorizedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/payslips/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/payroll/payslips/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.process"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void periodFilterOnMyPayslipsOnlyReturnsThatPeriod() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response runA = finalizedRunWithOnePayslip(6, employeeRef, "maker-1", "checker-1");
        PayrollRunDtos.Response runB = finalizeAnotherRunForTheSameEmployee(7, employeeRef, "maker-2", "checker-2");
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeRef));

        mockMvc.perform(get("/api/v1/payroll/payslips/me")
                        .param("periodId", runA.periodId().toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].runId").value(runA.id().toString()));

        mockMvc.perform(get("/api/v1/payroll/payslips/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        assertThat(runB.id()).isNotEqualTo(runA.id());
    }

    @Test
    void ytdSumsRealFinalizedLinesWithinTheSameFinancialYearAndTaxIsReportedAsNotConfiguredWhenNoTdsComponentExists() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        // Two periods, same employee, both within one financial year (Apr-Mar) - month 8 and 9 of the same fiscal run year.
        PayrollRunDtos.Response first = finalizedRunWithOnePayslip(8, employeeRef, "maker-1", "checker-1");
        PayrollRunDtos.Response second = finalizeAnotherRunForTheSameEmployee(9, employeeRef, "maker-2", "checker-2");
        Payslip secondPayslip = payslipRepository.findByRunIdAndEmployeeRef(second.id(), employeeRef).orElseThrow();
        when(employeeClient.resolveSelfEmployeeRef()).thenReturn(Optional.of(employeeRef));

        mockMvc.perform(get("/api/v1/payroll/payslips/" + secondPayslip.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ytd.available").value(true))
                .andExpect(jsonPath("$.ytd.grossPay").value(140000.00))
                .andExpect(jsonPath("$.tax.configured").value(false))
                .andExpect(jsonPath("$.tax.ytdAmount").value(org.hamcrest.Matchers.nullValue()));

        assertThat(first.id()).isNotEqualTo(second.id());
    }
}
