package com.growdigitalbridge.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.api.dto.EmployeeCompensationDtos;
import com.growdigitalbridge.payroll.api.dto.PayComponentDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollExceptionDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollPeriodDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.api.dto.StatutoryProfileDtos;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.CompensationStatus;
import com.growdigitalbridge.payroll.domain.StatutoryApplicabilityStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Employee Compensation Management + Statutory Profile + Payroll Exceptions, against real
 * PostgreSQL/RabbitMQ. Employee Service is mocked at its client boundary, exactly like every
 * other Payroll integration test.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CompensationManagementIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmployeeClient employeeClient;

    private static final SimpleGrantedAuthority PROCESS = new SimpleGrantedAuthority("payroll.process");
    private static final SimpleGrantedAuthority READ_ALL = new SimpleGrantedAuthority("payroll.read.all");

    private EmployeeCompensationDtos.ComponentRequest earning(String code, String amount) {
        return new EmployeeCompensationDtos.ComponentRequest(code, CompensationComponentType.EARNING, new BigDecimal(amount), null, null);
    }

    private EmployeeCompensationDtos.ComponentRequest deduction(String code, String amount) {
        return new EmployeeCompensationDtos.ComponentRequest(code, CompensationComponentType.DEDUCTION, new BigDecimal(amount), null, null);
    }

    private EmployeeCompensationDtos.Response createCompensation(UUID employeeRef, LocalDate from, LocalDate to) throws Exception {
        var request = new EmployeeCompensationDtos.CreateRequest(employeeRef, from, to,
                List.of(earning("BASIC_SALARY", "50000.00"), deduction("PF", "6000.00")));
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), EmployeeCompensationDtos.Response.class);
    }

    // --- Compensation: create/retrieve/validation ---

    @Test
    void createRetrieveAndListCompensationSucceeds() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        EmployeeCompensationDtos.Response created = createCompensation(employeeRef, LocalDate.of(2031, 1, 1), null);

        assertThat(created.employeeRef()).isEqualTo(employeeRef);
        assertThat(created.currency()).isEqualTo("INR");
        assertThat(created.payFrequency()).isEqualTo("MONTHLY");
        assertThat(created.status()).isEqualTo(CompensationStatus.ACTIVE);
        assertThat(created.components()).hasSize(2);

        mockMvc.perform(get("/api/v1/payroll/compensations/" + created.id()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeRef").value(employeeRef.toString()));

        mockMvc.perform(get("/api/v1/payroll/compensations").param("employeeRef", employeeRef.toString()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void creatingCompensationWithAnUnknownPayComponentCodeIsRejected() throws Exception {
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(), LocalDate.of(2031, 2, 1), null,
                List.of(earning("NOT_A_REAL_COMPONENT_CODE", "1000.00")));
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void creatingCompensationWithAWrongTypeForAnExistingComponentCodeIsRejected() throws Exception {
        // BASIC_SALARY is catalogued as EARNING, not DEDUCTION.
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(), LocalDate.of(2031, 2, 1), null,
                List.of(deduction("BASIC_SALARY", "1000.00")));
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void creatingCompensationWithEffectiveToBeforeEffectiveFromIsRejected() throws Exception {
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(),
                LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 1), List.of(earning("BASIC_SALARY", "1000.00")));
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void overlappingActiveCompensationForTheSameEmployeeIsRejected() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        createCompensation(employeeRef, LocalDate.of(2031, 4, 1), null);

        var overlapping = new EmployeeCompensationDtos.CreateRequest(employeeRef, LocalDate.of(2031, 5, 1), LocalDate.of(2031, 6, 1),
                List.of(earning("BASIC_SALARY", "1000.00")));
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapping)))
                .andExpect(status().isConflict());
    }

    @Test
    void nonOverlappingSequentialCompensationRecordsAreAllowed() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        createCompensation(employeeRef, LocalDate.of(2031, 7, 1), LocalDate.of(2031, 7, 31));

        var sequential = new EmployeeCompensationDtos.CreateRequest(employeeRef, LocalDate.of(2031, 8, 1), null,
                List.of(earning("BASIC_SALARY", "55000.00")));
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sequential)))
                .andExpect(status().isCreated());
    }

    // --- Compensation: update + immutability ---

    @Test
    void updatingCompensationBeforeAnyFinalizedUsageSucceeds() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        EmployeeCompensationDtos.Response created = createCompensation(employeeRef, LocalDate.of(2031, 9, 1), null);

        var update = new EmployeeCompensationDtos.UpdateRequest(LocalDate.of(2031, 9, 1), LocalDate.of(2031, 12, 31),
                CompensationStatus.ACTIVE, List.of(earning("BASIC_SALARY", "52000.00")));
        mockMvc.perform(patch("/api/v1/payroll/compensations/" + created.id())
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2031-12-31"))
                .andExpect(jsonPath("$.components.length()").value(1));
    }

    @Test
    void compensationUsedByAFinalizedRunCanNoLongerBeUpdated() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        EmployeeCompensationDtos.Response created = createCompensation(employeeRef, LocalDate.of(year, 1, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));

        UUID periodId = createPeriod(year, 1);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/submit").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/approve")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/finalize")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-1")).authorities(new SimpleGrantedAuthority("payroll.approve"))))
                .andExpect(status().isOk());

        var update = new EmployeeCompensationDtos.UpdateRequest(LocalDate.of(year, 1, 1), null,
                CompensationStatus.ACTIVE, List.of(earning("BASIC_SALARY", "99999.00")));
        mockMvc.perform(patch("/api/v1/payroll/compensations/" + created.id())
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- Compensation: authorization ---

    @Test
    void compensationWritesRequirePayrollProcessNotJustReadAll() throws Exception {
        var request = new EmployeeCompensationDtos.CreateRequest(UUID.randomUUID(), LocalDate.of(2031, 10, 1), null,
                List.of(earning("BASIC_SALARY", "1000.00")));
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(READ_ALL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployeeTokenWithOnlyPayslipAuthoritiesCannotReachCompensationEndpointsAtAll() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/compensations").param("employeeRef", UUID.randomUUID().toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("payslip.read.self")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeCompensationDtos.CreateRequest(
                                UUID.randomUUID(), LocalDate.of(2031, 11, 1), null, List.of(earning("BASIC_SALARY", "1000.00"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCompensationRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/compensations").param("employeeRef", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    // --- Pay components ---

    @Test
    void createRetrieveAndUpdatePayComponentSucceeds() throws Exception {
        String code = "TEST_COMPONENT_" + UUID.randomUUID().toString().substring(0, 8);
        var request = new PayComponentDtos.CreateRequest(code, "Test Component", CompensationComponentType.EARNING);
        MvcResult created = mockMvc.perform(post("/api/v1/payroll/pay-components")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        PayComponentDtos.Response response = objectMapper.readValue(created.getResponse().getContentAsString(), PayComponentDtos.Response.class);

        var update = new PayComponentDtos.UpdateRequest("Renamed Test Component", false);
        mockMvc.perform(patch("/api/v1/payroll/pay-components/" + response.id())
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Test Component"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/payroll/pay-components/" + response.id()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void duplicatePayComponentCodeIsRejected() throws Exception {
        String code = "DUP_COMPONENT_" + UUID.randomUUID().toString().substring(0, 8);
        var request = new PayComponentDtos.CreateRequest(code, "First", CompensationComponentType.EARNING);
        mockMvc.perform(post("/api/v1/payroll/pay-components")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        var duplicate = new PayComponentDtos.CreateRequest(code, "Second", CompensationComponentType.DEDUCTION);
        mockMvc.perform(post("/api/v1/payroll/pay-components")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayComponentTypeIsRejectedAsAValidationError() throws Exception {
        String body = """
                {"code":"BAD_TYPE_COMPONENT","name":"Bad Type","type":"NOT_A_REAL_TYPE"}""";
        mockMvc.perform(post("/api/v1/payroll/pay-components")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- Statutory profile ---

    @Test
    void statutoryProfileUpsertAndRetrieveRoundTrips() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        var request = new StatutoryProfileDtos.UpsertRequest(
                StatutoryApplicabilityStatus.APPLICABLE, "100123456789", "MEMBER-1", LocalDate.of(2031, 1, 1), null,
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null,
                StatutoryApplicabilityStatus.APPLICABLE, "Karnataka", LocalDate.of(2031, 1, 1), null, null);

        mockMvc.perform(put("/api/v1/payroll/statutory-profiles/" + employeeRef)
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfStatus").value("APPLICABLE"))
                .andExpect(jsonPath("$.esiStatus").value("NOT_APPLICABLE"));

        mockMvc.perform(get("/api/v1/payroll/statutory-profiles/" + employeeRef).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfUan").value("100123456789"))
                .andExpect(jsonPath("$.ptJurisdiction").value("Karnataka"));
    }

    @Test
    void statutoryProfileNotApplicableIsNeverFlaggedAsMissingEvenWithoutAnIdentifier() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        var request = new StatutoryProfileDtos.UpsertRequest(
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null, null,
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null,
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null, null);
        upsertStatutoryProfile(employeeRef, request);

        int year = Year.now().getValue() + 5;
        createCompensation(employeeRef, LocalDate.of(year, 2, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 2);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionCount").value(0));
    }

    @Test
    void missingPfIdentifierWhileApplicableProducesAPayrollException() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        var request = new StatutoryProfileDtos.UpsertRequest(
                StatutoryApplicabilityStatus.APPLICABLE, null, null, null, null,
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null,
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null, null);
        upsertStatutoryProfile(employeeRef, request);

        int year = Year.now().getValue() + 5;
        createCompensation(employeeRef, LocalDate.of(year, 3, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 3);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionCount").value(1));

        mockMvc.perform(get("/api/v1/payroll/exceptions").param("runId", run.id().toString()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("MISSING_PF_IDENTIFIER"))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"));
    }

    @Test
    void missingEsiIdentifierWhilePendingVerificationProducesAPayrollException() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        var request = new StatutoryProfileDtos.UpsertRequest(
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null, null,
                StatutoryApplicabilityStatus.PENDING_VERIFICATION, null, null, null,
                StatutoryApplicabilityStatus.NOT_APPLICABLE, null, null, null, null);
        upsertStatutoryProfile(employeeRef, request);

        int year = Year.now().getValue() + 5;
        createCompensation(employeeRef, LocalDate.of(year, 4, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 4);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionCount").value(1));
    }

    @Test
    void entirelyMissingStatutoryProfileProducesAPayrollExceptionAlongsideTheLine() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        createCompensation(employeeRef, LocalDate.of(year, 5, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 5);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.exceptionCount").value(1));

        mockMvc.perform(get("/api/v1/payroll/exceptions").param("runId", run.id().toString()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("MISSING_STATUTORY_PROFILE"));
    }

    // --- Payroll exception resolution ---

    @Test
    void anOpenExceptionCanBeResolvedButNotResolvedTwice() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        createCompensation(employeeRef, LocalDate.of(year, 6, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 6);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk());

        MvcResult listResult = mockMvc.perform(get("/api/v1/payroll/exceptions").param("runId", run.id().toString())
                        .with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk()).andReturn();
        var page = objectMapper.readTree(listResult.getResponse().getContentAsString());
        UUID exceptionId = UUID.fromString(page.get("items").get(0).get("id").asText());

        mockMvc.perform(post("/api/v1/payroll/exceptions/" + exceptionId + "/resolve").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedBy").isNotEmpty());

        mockMvc.perform(post("/api/v1/payroll/exceptions/" + exceptionId + "/resolve").with(jwt().authorities(PROCESS)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resolvingAnExceptionRequiresPayrollProcess() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        createCompensation(employeeRef, LocalDate.of(year, 7, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 7);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");
        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk());

        MvcResult listResult = mockMvc.perform(get("/api/v1/payroll/exceptions").param("runId", run.id().toString())
                        .with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk()).andReturn();
        var page = objectMapper.readTree(listResult.getResponse().getContentAsString());
        UUID exceptionId = UUID.fromString(page.get("items").get(0).get("id").asText());

        mockMvc.perform(post("/api/v1/payroll/exceptions/" + exceptionId + "/resolve").with(jwt().authorities(READ_ALL)))
                .andExpect(status().isForbidden());
    }

    // --- Mid-period compensation resolution (Payroll V1 completion review fix) ---
    // CompensationResolver now resolves an ACTIVE compensation record whose effective range
    // OVERLAPS the payroll period, not only one already effective at the period's first day.

    @Test
    void compensationEffectiveBeforePeriodStartResolvesAndProducesALine() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        createCompensation(employeeRef, LocalDate.of(year, 1, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 8);
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1));
    }

    @Test
    void compensationEffectiveExactlyOnPeriodStartResolvesAndProducesALine() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        UUID periodId = createPeriod(year, 9);
        createCompensation(employeeRef, LocalDate.of(year, 9, 1), null);
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1));
    }

    /**
     * The defect this fix resolves: a new joiner (or any compensation revision) taking effect
     * partway through the period must still produce a {@code PayrollRunLine} - never silently
     * excluded with {@code NO_EFFECTIVE_COMPENSATION} merely because {@code effectiveFrom} falls
     * after the period's first day.
     */
    @Test
    void compensationEffectiveDuringThePeriodResolvesAndProducesALineInsteadOfNoEffectiveCompensation() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        UUID periodId = createPeriod(year, 10);
        createCompensation(employeeRef, LocalDate.of(year, 10, 15), null); // mid-period joiner
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(1));

        MvcResult exceptions = mockMvc.perform(get("/api/v1/payroll/exceptions").param("runId", run.id().toString())
                        .with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk()).andReturn();
        var items = objectMapper.readTree(exceptions.getResponse().getContentAsString()).get("items");
        for (var item : items) {
            assertThat(item.get("reason").asText()).isNotEqualTo("NO_EFFECTIVE_COMPENSATION");
        }
    }

    @Test
    void compensationEffectiveAfterThePeriodDoesNotResolveAndStillProducesNoEffectiveCompensation() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        UUID periodId = createPeriod(year, 11);
        createCompensation(employeeRef, LocalDate.of(year, 12, 1), null); // starts the month after this period ends
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(0))
                .andExpect(jsonPath("$.exceptionCount").value(1));

        mockMvc.perform(get("/api/v1/payroll/exceptions").param("runId", run.id().toString()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("NO_EFFECTIVE_COMPENSATION"));
    }

    /**
     * Resolving by range-overlap (rather than a single point in time) has one unavoidable
     * consequence: two individually-valid, sequential compensation records (non-overlapping with
     * EACH OTHER, so {@code assertNoOverlap} allows both) can each independently overlap the SAME
     * payroll period - e.g. an old record ending mid-month and a new one starting mid-month.
     * Deciding which one (or how to split between them) is a proration question this fix does not
     * invent an answer to; the existing ambiguity-handling mechanism (more than one row resolves)
     * must still fail the run safely rather than silently guess - exactly as it already does for a
     * literal date-range overlap.
     */
    @Test
    void twoSequentialCompensationRecordsBothOverlappingTheSamePeriodFailCalculationSafelyAsAmbiguous() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        int year = Year.now().getValue() + 5;
        UUID periodId = createPeriod(year, 12);
        createCompensation(employeeRef, LocalDate.of(year, 12, 1), LocalDate.of(year, 12, 10));
        var secondRequest = new EmployeeCompensationDtos.CreateRequest(employeeRef, LocalDate.of(year, 12, 15), null,
                List.of(earning("BASIC_SALARY", "60000.00")));
        mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated());
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        PayrollRunDtos.Response run = createRun(periodId, "maker-1");

        mockMvc.perform(post("/api/v1/payroll/runs/" + run.id() + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().is5xxServerError());

        MvcResult afterFailure = mockMvc.perform(get("/api/v1/payroll/runs/" + run.id()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk()).andReturn();
        PayrollRunDtos.Response reread = objectMapper.readValue(afterFailure.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
        assertThat(reread.status().name()).isEqualTo("CALCULATION_FAILED");
    }

    // --- shared helpers ---

    private void upsertStatutoryProfile(UUID employeeRef, StatutoryProfileDtos.UpsertRequest request) throws Exception {
        mockMvc.perform(put("/api/v1/payroll/statutory-profiles/" + employeeRef)
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private UUID createPeriod(int year, int month) throws Exception {
        var request = new PayrollPeriodDtos.CreateRequest(year, month, null);
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/periods")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PayrollPeriodDtos.Response.class).id();
    }

    private PayrollRunDtos.Response createRun(UUID periodId, String actor) throws Exception {
        var request = new PayrollRunDtos.CreateRequest(periodId);
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/runs")
                        .with(jwt().jwt(jwt -> jwt.subject(actor)).authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
    }
}
