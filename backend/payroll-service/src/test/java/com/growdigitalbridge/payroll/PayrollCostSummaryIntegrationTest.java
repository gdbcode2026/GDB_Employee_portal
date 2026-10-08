package com.growdigitalbridge.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.api.dto.PayrollPeriodDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayFrequency;
import com.growdigitalbridge.payroll.repository.CompensationComponentRepository;
import com.growdigitalbridge.payroll.repository.EmployeeCompensationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reporting V1 D3 (Payroll Cost Summary), against real PostgreSQL/RabbitMQ
 * (docs/REPORTING_V1_REQUIREMENTS.md Section D3). Employee Service is mocked at its client
 * boundary, exactly like {@code PayrollIntegrationTest}/{@code PayrollAdjustmentIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PayrollCostSummaryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EmployeeCompensationRepository compensationRepository;
    @Autowired private CompensationComponentRepository compensationComponentRepository;

    @MockitoBean private EmployeeClient employeeClient;

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

    private PayrollRunDtos.Response createAdjustment(UUID originalRunId, String actor) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/runs/" + originalRunId + "/adjustments")
                        .with(jwt().jwt(jwt -> jwt.subject(actor)).authorities(new SimpleGrantedAuthority("payroll.process"))))
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

    private PayrollRunDtos.Response finalizedRun(int month, UUID employeeRef, String maker, String checker) throws Exception {
        demoCompensation(employeeRef, LocalDate.of(Year.now().getValue() + 5, 1, 1));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(Year.now().getValue() + 5, month);
        PayrollRunDtos.Response run = createRun(periodId, maker);
        return processSubmitApproveFinalize(run.id(), maker, checker);
    }

    @Test
    void financeWithPayrollReadAllReceivesTheCostSummary() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response run = finalizedRun(1, employeeRef, "maker-1", "checker-1");

        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.runId=='" + run.id() + "')].totalGrossPay").value(70000.0))
                .andExpect(jsonPath("$.items[?(@.runId=='" + run.id() + "')].totalDeductions").value(6000.0))
                .andExpect(jsonPath("$.items[?(@.runId=='" + run.id() + "')].totalEmployerContributions").value(6000.0))
                .andExpect(jsonPath("$.items[?(@.runId=='" + run.id() + "')].totalNetPay").value(64000.0));
    }

    @Test
    void anEmployeeTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.self"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aManagerTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("employee.read.team"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("organization.manage"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void defaultStatusFilterIsFinalizedOnly() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        demoCompensation(employeeRef, LocalDate.of(Year.now().getValue() + 5, 1, 1));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(Year.now().getValue() + 5, 2);
        PayrollRunDtos.Response draftRun = createRun(periodId, "maker-1");

        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.runId=='" + draftRun.id() + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary?status=DRAFT")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.runId=='" + draftRun.id() + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.runId=='" + draftRun.id() + "')].totalGrossPay").value(0));
    }

    @Test
    void periodIdFilterIsolatesOneRunAcrossMultiplePeriods() throws Exception {
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();
        PayrollRunDtos.Response runA = finalizedRun(3, employeeA, "maker-1", "checker-1");
        PayrollRunDtos.Response runB = finalizedRun(4, employeeB, "maker-1", "checker-1");

        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary?periodId=" + runA.periodId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.runId=='" + runA.id() + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.runId=='" + runB.id() + "')]").isEmpty());
    }

    @Test
    void regularAndAdjustmentFinalizedRunsAppearAsSeparateRowsNeverNetted() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        PayrollRunDtos.Response original = finalizedRun(5, employeeRef, "maker-1", "checker-1");
        PayrollRunDtos.Response adjustment = createAdjustment(original.id(), "maker-2");
        processSubmitApproveFinalize(adjustment.id(), "maker-2", "checker-2");

        MvcResult result = mockMvc.perform(get("/api/v1/payroll/runs/cost-summary?periodId=" + original.periodId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.runId=='" + original.id() + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.runId=='" + adjustment.id() + "')]").exists())
                .andReturn();

        // Two distinct rows, not one merged/netted total for the period.
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(original.id().toString());
        assertThat(body).contains(adjustment.id().toString());
    }

    @Test
    void emptyResultIsHandledCorrectlyWhenNoFinalizedRunsExist() throws Exception {
        UUID periodId = createPeriod(Year.now().getValue() + 6, 1);

        mockMvc.perform(get("/api/v1/payroll/runs/cost-summary?periodId=" + periodId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.total").value(0));
    }

    @Test
    void responseContainsNoEmployeeLevelSensitiveFields() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        finalizedRun(6, employeeRef, "maker-1", "checker-1");

        MvcResult result = mockMvc.perform(get("/api/v1/payroll/runs/cost-summary")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payroll.read.all"))))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        // The employee's own identifier, and every employee/payslip-level field name, must never
        // appear in a run-level aggregate report.
        assertThat(body).doesNotContain(employeeRef.toString());
        assertThat(body).doesNotContain("employeeRef");
        assertThat(body).doesNotContain("componentBreakdown");
        assertThat(body).doesNotContain("bankAccount");
        assertThat(body).doesNotContain("statutoryIdentifier");
    }
}
