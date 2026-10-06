package com.growdigitalbridge.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.api.dto.EmployeeCompensationDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollPeriodDtos;
import com.growdigitalbridge.payroll.api.dto.PayrollRunDtos;
import com.growdigitalbridge.payroll.api.dto.StatutoryProfileDtos;
import com.growdigitalbridge.payroll.api.dto.StatutoryRuleDtos;
import com.growdigitalbridge.payroll.client.EmployeeClient;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.PayrollException;
import com.growdigitalbridge.payroll.domain.PayrollExceptionReason;
import com.growdigitalbridge.payroll.domain.PayrollRunLine;
import com.growdigitalbridge.payroll.domain.StatutoryApplicabilityStatus;
import com.growdigitalbridge.payroll.domain.StatutoryRuleCalculationType;
import com.growdigitalbridge.payroll.domain.StatutoryRuleType;
import com.growdigitalbridge.payroll.repository.PayrollExceptionRepository;
import com.growdigitalbridge.payroll.repository.PayrollRunLineRepository;
import java.math.BigDecimal;
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
 * Configurable Statutory + Tax Rule Engine: rule management API (create/version/activate/
 * deactivate/validation/security) and its integration into the real calculation pipeline, against
 * real PostgreSQL/RabbitMQ. Employee Service is mocked at its client boundary, exactly like every
 * other Payroll integration test. Every amount/percentage/jurisdiction used below is explicit,
 * synthetic TEST DATA - never a real PF/ESI/Professional Tax/TDS value.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class StatutoryRuleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PayrollRunLineRepository lineRepository;
    @Autowired private PayrollExceptionRepository exceptionRepository;

    @MockitoBean private EmployeeClient employeeClient;

    private static final SimpleGrantedAuthority PROCESS = new SimpleGrantedAuthority("payroll.process");
    private static final SimpleGrantedAuthority APPROVE = new SimpleGrantedAuthority("payroll.approve");
    private static final SimpleGrantedAuthority READ_ALL = new SimpleGrantedAuthority("payroll.read.all");

    // --- Rule management: create/version/validate ---

    @Test
    void createRetrieveAndListRuleByCodeSucceeds() throws Exception {
        String code = "TEST_PF_" + UUID.randomUUID();
        StatutoryRuleDtos.Response created = createRule(code, StatutoryRuleType.PF, null,
                LocalDate.of(2031, 1, 1), null, fixedAmount("500.00"));
        assertThat(created.ruleVersion()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/payroll/statutory-rules/" + created.id()).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/payroll/statutory-rules").param("code", code).with(jwt().authorities(READ_ALL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void creatingTheNextVersionOfAnExistingCodeIncrementsTheVersionNumber() throws Exception {
        String code = "TEST_PF_" + UUID.randomUUID();
        createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), LocalDate.of(2031, 6, 30), fixedAmount("500.00"));
        StatutoryRuleDtos.Response v2 = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 7, 1), null, fixedAmount("600.00"));

        assertThat(v2.ruleVersion()).isEqualTo(2);
    }

    @Test
    void creatingANextVersionWithAMismatchedRuleTypeIsRejected() throws Exception {
        String code = "TEST_MISMATCH_" + UUID.randomUUID();
        createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), null, fixedAmount("500.00"));

        var request = new StatutoryRuleDtos.CreateRequest(code, StatutoryRuleType.ESI, null, null,
                LocalDate.of(2031, 7, 1), null, StatutoryRuleCalculationType.FIXED_AMOUNT, fixedAmount("500.00"));
        mockMvc.perform(post("/api/v1/payroll/statutory-rules")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void creatingARuleWithInvalidEffectiveDatesIsRejected() throws Exception {
        var request = new StatutoryRuleDtos.CreateRequest("TEST_BAD_DATES_" + UUID.randomUUID(), StatutoryRuleType.PF, null, null,
                LocalDate.of(2031, 6, 1), LocalDate.of(2031, 1, 1), StatutoryRuleCalculationType.FIXED_AMOUNT, fixedAmount("500.00"));
        mockMvc.perform(post("/api/v1/payroll/statutory-rules")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- Rule management: lifecycle/overlap ---

    @Test
    void updatingANonDraftRuleIsRejected() throws Exception {
        String code = "TEST_LOCK_" + UUID.randomUUID();
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), null, fixedAmount("500.00"));
        activateRule(rule.id());

        var update = new StatutoryRuleDtos.UpdateRequest(LocalDate.of(2031, 1, 1), null,
                StatutoryRuleCalculationType.FIXED_AMOUNT, fixedAmount("999.00"));
        mockMvc.perform(patch("/api/v1/payroll/statutory-rules/" + rule.id())
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void activatingAnAlreadyActiveRuleIsRejected() throws Exception {
        String code = "TEST_DOUBLE_ACTIVATE_" + UUID.randomUUID();
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), null, fixedAmount("500.00"));
        activateRule(rule.id());

        mockMvc.perform(post("/api/v1/payroll/statutory-rules/" + rule.id() + "/activate").with(jwt().authorities(APPROVE)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void activatingAnOverlappingVersionOfTheSameCodeIsRejectedAsConflict() throws Exception {
        String code = "TEST_OVERLAP_" + UUID.randomUUID();
        StatutoryRuleDtos.Response v1 = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), null, fixedAmount("500.00"));
        activateRule(v1.id());

        StatutoryRuleDtos.Response v2 = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 6, 1), null, fixedAmount("600.00"));
        mockMvc.perform(post("/api/v1/payroll/statutory-rules/" + v2.id() + "/activate").with(jwt().authorities(APPROVE)))
                .andExpect(status().isConflict());
    }

    @Test
    void activatingASequentialNonOverlappingVersionSucceeds() throws Exception {
        String code = "TEST_SEQUENTIAL_" + UUID.randomUUID();
        StatutoryRuleDtos.Response v1 = createRule(code, StatutoryRuleType.PF, null,
                LocalDate.of(2031, 1, 1), LocalDate.of(2031, 5, 31), fixedAmount("500.00"));
        activateRule(v1.id());

        StatutoryRuleDtos.Response v2 = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 6, 1), null, fixedAmount("600.00"));
        mockMvc.perform(post("/api/v1/payroll/statutory-rules/" + v2.id() + "/activate").with(jwt().authorities(APPROVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void activatingTheSameCodeForADifferentJurisdictionNeverConflicts() throws Exception {
        String code = "TEST_PT_" + UUID.randomUUID();
        StatutoryRuleDtos.Response karnataka = createRule(code, StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka",
                LocalDate.of(2031, 1, 1), null, fixedAmount("200.00"));
        activateRule(karnataka.id());

        StatutoryRuleDtos.Response maharashtra = createRule(code, StatutoryRuleType.PROFESSIONAL_TAX, "Maharashtra",
                LocalDate.of(2031, 1, 1), null, fixedAmount("300.00"));
        mockMvc.perform(post("/api/v1/payroll/statutory-rules/" + maharashtra.id() + "/activate").with(jwt().authorities(APPROVE)))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatingAnActiveRuleSucceeds() throws Exception {
        String code = "TEST_DEACTIVATE_" + UUID.randomUUID();
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), null, fixedAmount("500.00"));
        activateRule(rule.id());

        mockMvc.perform(post("/api/v1/payroll/statutory-rules/" + rule.id() + "/deactivate").with(jwt().authorities(APPROVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // --- Rule management: security ---

    @Test
    void creatingARuleRequiresPayrollProcess() throws Exception {
        var request = new StatutoryRuleDtos.CreateRequest("TEST_SEC_" + UUID.randomUUID(), StatutoryRuleType.PF, null, null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.FIXED_AMOUNT, fixedAmount("500.00"));
        mockMvc.perform(post("/api/v1/payroll/statutory-rules")
                        .with(jwt().authorities(READ_ALL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void activatingARuleRequiresPayrollApproveNotJustPayrollProcess() throws Exception {
        String code = "TEST_SEC_ACTIVATE_" + UUID.randomUUID();
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), null, fixedAmount("500.00"));

        mockMvc.perform(post("/api/v1/payroll/statutory-rules/" + rule.id() + "/activate").with(jwt().authorities(PROCESS)))
                .andExpect(status().isForbidden());
    }

    // --- Calculation integration ---

    @Test
    void fixedAmountRuleComputesTheConfiguredAmountForTheWiredComponent() throws Exception {
        String code = "TEST_PF_FIXED_" + UUID.randomUUID();
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2000, 1, 1), null, fixedAmount("4321.00"));
        activateRule(rule.id());

        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 1, 1),
                List.of(earning("BASIC_SALARY", "50000.00"), deductionWithRule("PF", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 1);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        assertThat(pfFinalAmount(line)).isEqualByComparingTo("4321.00");
        assertThat(exceptionsFor(run.id(), employeeRef)).isEmpty();
    }

    @Test
    void percentageRuleComputesAPercentageOfGrossEarnings() throws Exception {
        String code = "TEST_PF_PCT_" + UUID.randomUUID();
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2000, 1, 1), null,
                new StatutoryRuleDtos.ParametersRequest(null, new BigDecimal("10"), null, null, null, null, null));
        activateRule(rule.id());

        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 2, 1),
                List.of(earning("BASIC_SALARY", "40000.00"), earning("HRA", "10000.00"), deductionWithRule("PF", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 2);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        // 10% of gross earnings (40000 + 10000 = 50000) = 5000.00.
        assertThat(pfFinalAmount(line)).isEqualByComparingTo("5000.00");
    }

    @Test
    void thresholdBasedRuleBelowMinWageProducesStatutoryRuleNotApplicableExceptionWithoutBlockingTheLine() throws Exception {
        String code = "TEST_PF_THRESHOLD_" + UUID.randomUUID();
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(2000, 1, 1), null,
                new StatutoryRuleDtos.ParametersRequest(null, new BigDecimal("12"), null, new BigDecimal("25000.00"), null, null, null));
        activateRule(rule.id());

        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 3, 1),
                List.of(earning("BASIC_SALARY", "15000.00"), deductionWithRule("PF", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 3);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        assertThat(pfFinalAmount(line)).isEqualByComparingTo("0.00");
        assertThat(exceptionsFor(run.id(), employeeRef)).extracting(PayrollException::getReason)
                .containsExactly(PayrollExceptionReason.STATUTORY_RULE_NOT_APPLICABLE);
    }

    @Test
    void aComponentWiredToANonExistentRuleCodeProducesMissingStatutoryRuleExceptionWithoutBlockingTheLine() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 4, 1),
                List.of(earning("BASIC_SALARY", "20000.00"), deductionWithRule("PF", "NO_SUCH_RULE_" + UUID.randomUUID())));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 4);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        assertThat(pfFinalAmount(line)).isEqualByComparingTo("0.00");
        assertThat(exceptionsFor(run.id(), employeeRef)).extracting(PayrollException::getReason)
                .contains(PayrollExceptionReason.MISSING_STATUTORY_RULE);
    }

    @Test
    void aTdsComponentWiredToANonExistentRuleCodeProducesTaxConfigurationRequired() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 5, 1),
                List.of(earning("BASIC_SALARY", "20000.00"), deductionWithRule("TDS", "NO_SUCH_TDS_RULE_" + UUID.randomUUID())));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 5);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        assertThat(exceptionsFor(run.id(), employeeRef)).extracting(PayrollException::getReason)
                .contains(PayrollExceptionReason.TAX_CONFIGURATION_REQUIRED);
    }

    @Test
    void aTdsComponentWithNoCalculationStrategyCodeProducesMissingTaxConfiguration() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 6, 1),
                List.of(earning("BASIC_SALARY", "20000.00"), deduction("TDS", "0.00")));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 6);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        assertThat(exceptionsFor(run.id(), employeeRef)).extracting(PayrollException::getReason)
                .contains(PayrollExceptionReason.MISSING_TAX_CONFIGURATION);
    }

    @Test
    void anEmployeeWithNoTdsComponentAtAllNeverGetsMissingTaxConfiguration() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 7, 1),
                List.of(earning("BASIC_SALARY", "20000.00"), deduction("PF", "2400.00")));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 7);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        assertThat(exceptionsFor(run.id(), employeeRef)).extracting(PayrollException::getReason)
                .doesNotContain(PayrollExceptionReason.MISSING_TAX_CONFIGURATION);
    }

    @Test
    void professionalTaxRuleResolvesByTheEmployeesOwnJurisdictionNotAnother() throws Exception {
        String code = "TEST_PT_RESOLVE_" + UUID.randomUUID();
        StatutoryRuleDtos.Response karnataka = createRule(code, StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka",
                LocalDate.of(2000, 1, 1), null, fixedAmount("200.00"));
        activateRule(karnataka.id());
        StatutoryRuleDtos.Response maharashtra = createRule(code, StatutoryRuleType.PROFESSIONAL_TAX, "Maharashtra",
                LocalDate.of(2000, 1, 1), null, fixedAmount("300.00"));
        activateRule(maharashtra.id());

        UUID employeeRef = UUID.randomUUID();
        upsertStatutoryProfile(employeeRef, new StatutoryProfileDtos.UpsertRequest(
                StatutoryApplicabilityStatus.APPLICABLE, "UAN1", "MEM1", LocalDate.of(2000, 1, 1), null,
                StatutoryApplicabilityStatus.APPLICABLE, "ESI1", LocalDate.of(2000, 1, 1), null,
                StatutoryApplicabilityStatus.APPLICABLE, "Karnataka", LocalDate.of(2000, 1, 1), null, null));

        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 8, 1),
                List.of(earning("BASIC_SALARY", "20000.00"), deductionWithRule("PROFESSIONAL_TAX", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 8);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        assertThat(componentFinalAmount(line, "PROFESSIONAL_TAX")).isEqualByComparingTo("200.00");
    }

    @Test
    void aHistoricalRunRemainsReproducibleAfterANewerRuleVersionIsActivatedForALaterDate() throws Exception {
        // A dedicated year offset, used by no other test in this class, so no (year, month)
        // period collision is possible regardless of test execution order.
        int year = Year.now().getValue() + 11;
        String code = "TEST_PF_REPRO_" + UUID.randomUUID();
        StatutoryRuleDtos.Response v1 = createRule(code, StatutoryRuleType.PF, null,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 6, 30), fixedAmount("100.00"));
        activateRule(v1.id());

        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        createCompensation(employeeRef, LocalDate.of(year, 1, 1),
                List.of(earning("BASIC_SALARY", "20000.00"), deductionWithRule("PF", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));

        // Run A: period in March, squarely inside v1's effective range.
        UUID firstPeriodId = createPeriod(year, 3);
        PayrollRunDtos.Response firstRun = createRun(firstPeriodId);
        processRun(firstRun.id());
        assertThat(pfFinalAmount(lineRepository.findByRunIdAndEmployeeRef(firstRun.id(), employeeRef).orElseThrow()))
                .isEqualByComparingTo("100.00");

        // A newer version is activated, effective only from July onward - disjoint from v1, so no overlap conflict.
        StatutoryRuleDtos.Response v2 = createRule(code, StatutoryRuleType.PF, null, LocalDate.of(year, 7, 1), null, fixedAmount("200.00"));
        activateRule(v2.id());

        // A CALCULATED run is not directly reprocessable (PayrollRunService.REPROCESSABLE) -
        // move it back to REJECTED first, mirroring the platform's own reprocess-before-
        // finalization pattern, then reprocess the SAME run.
        mockMvc.perform(post("/api/v1/payroll/runs/" + firstRun.id() + "/submit").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/runs/" + firstRun.id() + "/reject")
                        .with(jwt().jwt(jwt -> jwt.subject("checker-repro")).authorities(APPROVE)))
                .andExpect(status().isOk());

        // Reprocessing the SAME March run must still resolve v1 (its period's date never
        // changed) - activating a newer version for a later date range does not retroactively
        // change an earlier period's resolved rule. This is the reproducibility guarantee item 7
        // requires: resolution is strictly by period date, never "whichever version is newest."
        processRun(firstRun.id());
        assertThat(pfFinalAmount(lineRepository.findByRunIdAndEmployeeRef(firstRun.id(), employeeRef).orElseThrow()))
                .isEqualByComparingTo("100.00");

        // A brand-new run for a period inside v2's range correctly resolves the newer version.
        UUID secondPeriodId = createPeriod(year, 9);
        PayrollRunDtos.Response secondRun = createRun(secondPeriodId);
        processRun(secondRun.id());
        assertThat(pfFinalAmount(lineRepository.findByRunIdAndEmployeeRef(secondRun.id(), employeeRef).orElseThrow()))
                .isEqualByComparingTo("200.00");
    }

    // --- Architecture extension: SLAB_BASED / PROGRESSIVE_TAX / tax-regime-aware resolution ---

    @Test
    void slabBasedProfessionalTaxRuleAppliesTheSingleMatchingBracketsFixedAmount() throws Exception {
        String code = "TEST_PT_SLAB_" + UUID.randomUUID();
        var brackets = List.of(
                bracket(1, "0", "21000", "0", null),
                bracket(2, "21000", "30000", "180", null),
                bracket(3, "30000", null, "425", null));
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2000, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));
        activateRule(rule.id());

        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 11, 1),
                List.of(earning("BASIC_SALARY", "25000.00"), deductionWithRule("PROFESSIONAL_TAX", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 11);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        // 25000 falls in bracket 2 (21000-30000) -> flat fee 180, never summed with bracket 1.
        assertThat(componentFinalAmount(line, "PROFESSIONAL_TAX")).isEqualByComparingTo("180");
        assertThat(exceptionsFor(run.id(), employeeRef)).isEmpty();
    }

    @Test
    void progressiveTaxRuleAccumulatesMarginalRatesAcrossBracketsForTheNewTaxRegime() throws Exception {
        String code = "TEST_TDS_PROGRESSIVE_" + UUID.randomUUID();
        var brackets = List.of(
                bracket(1, "0", "400000", null, "0"),
                bracket(2, "400000", "800000", null, "5"),
                bracket(3, "800000", null, null, "10"));
        StatutoryRuleDtos.Response rule = createRule(code, StatutoryRuleType.TDS, null, "NEW_REGIME",
                LocalDate.of(2000, 1, 1), null, StatutoryRuleCalculationType.PROGRESSIVE_TAX,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));
        activateRule(rule.id());

        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef, "NEW_REGIME");
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 12, 1),
                List.of(earning("BASIC_SALARY", "900000.00"), deductionWithRule("TDS", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 12);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        // 0% of 400000 + 5% of next 400000 (20000) + 10% of the remaining 100000 (10000) = 30000.00.
        assertThat(componentFinalAmount(line, "TDS")).isEqualByComparingTo("30000.00");
        assertThat(exceptionsFor(run.id(), employeeRef)).isEmpty();
    }

    @Test
    void aTdsRuleForOneRegimeIsNeverResolvedForAnEmployeeElectingAnotherRegime() throws Exception {
        String code = "TEST_TDS_REGIME_" + UUID.randomUUID();
        StatutoryRuleDtos.Response newRegimeRule = createRule(code, StatutoryRuleType.TDS, null, "NEW_REGIME",
                LocalDate.of(2000, 1, 1), null, StatutoryRuleCalculationType.FIXED_AMOUNT, fixedAmount("1000.00"));
        activateRule(newRegimeRule.id());

        UUID employeeRef = UUID.randomUUID();
        // This employee elected the OLD regime - no rule exists under that regime for this code.
        fullyApplicableStatutoryProfile(employeeRef, "OLD_REGIME");
        // A dedicated year offset, used by no other test in this class, so no (year, month)
        // period collision is possible regardless of test execution order.
        int year = Year.now().getValue() + 12;
        createCompensation(employeeRef, LocalDate.of(year, 1, 1),
                List.of(earning("BASIC_SALARY", "50000.00"), deductionWithRule("TDS", code)));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 1);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        assertThat(componentFinalAmount(line, "TDS")).isEqualByComparingTo("0.00");
        assertThat(exceptionsFor(run.id(), employeeRef)).extracting(PayrollException::getReason)
                .contains(PayrollExceptionReason.TAX_CONFIGURATION_REQUIRED);
    }

    @Test
    void creatingASlabBasedRuleWithOverlappingBracketsIsRejected() throws Exception {
        var brackets = List.of(
                bracket(1, "0", "100", "10", null),
                bracket(2, "50", null, "20", null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT_OVERLAP_" + UUID.randomUUID(),
                StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null, LocalDate.of(2031, 1, 1), null,
                StatutoryRuleCalculationType.SLAB_BASED, new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));
        mockMvc.perform(post("/api/v1/payroll/statutory-rules")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- Regression ---

    @Test
    void aComponentWithNoCalculationStrategyCodeStillUsesTheConfiguredFixedAmountUnchanged() throws Exception {
        UUID employeeRef = UUID.randomUUID();
        fullyApplicableStatutoryProfile(employeeRef);
        int year = Year.now().getValue() + 10;
        createCompensation(employeeRef, LocalDate.of(year, 10, 1),
                List.of(earning("BASIC_SALARY", "50000.00"), deduction("PF", "6000.00")));
        when(employeeClient.resolveEmployeeRefsEligibleForPeriod(any(), any())).thenReturn(Set.of(employeeRef));
        UUID periodId = createPeriod(year, 10);
        PayrollRunDtos.Response run = createRun(periodId);
        processRun(run.id());

        PayrollRunLine line = lineRepository.findByRunIdAndEmployeeRef(run.id(), employeeRef).orElseThrow();
        assertThat(pfFinalAmount(line)).isEqualByComparingTo("6000.00");
        assertThat(line.getGrossPay()).isEqualByComparingTo("50000.00");
        assertThat(exceptionsFor(run.id(), employeeRef)).isEmpty();
    }

    // --- shared helpers ---

    private StatutoryRuleDtos.ParametersRequest fixedAmount(String amount) {
        return new StatutoryRuleDtos.ParametersRequest(new BigDecimal(amount), null, null, null, null, null, null);
    }

    private StatutoryRuleDtos.BracketRequest bracket(int order, String lowerBound, String upperBound, String fixedAmount, String percentage) {
        return new StatutoryRuleDtos.BracketRequest(order, new BigDecimal(lowerBound),
                upperBound == null ? null : new BigDecimal(upperBound),
                fixedAmount == null ? null : new BigDecimal(fixedAmount),
                percentage == null ? null : new BigDecimal(percentage));
    }

    private StatutoryRuleDtos.Response createRule(String code, StatutoryRuleType type, String jurisdiction,
                                                    LocalDate from, LocalDate to, StatutoryRuleDtos.ParametersRequest params) throws Exception {
        boolean isThresholdBased = params.minWage() != null || params.maxWage() != null || params.cap() != null;
        StatutoryRuleCalculationType calculationType = isThresholdBased ? StatutoryRuleCalculationType.THRESHOLD_BASED
                : params.percentage() != null ? StatutoryRuleCalculationType.PERCENTAGE
                : StatutoryRuleCalculationType.FIXED_AMOUNT;
        return createRule(code, type, jurisdiction, null, from, to, calculationType, params);
    }

    private StatutoryRuleDtos.Response createRule(String code, StatutoryRuleType type, String jurisdiction, String taxRegime,
                                                    LocalDate from, LocalDate to, StatutoryRuleCalculationType calculationType,
                                                    StatutoryRuleDtos.ParametersRequest params) throws Exception {
        var request = new StatutoryRuleDtos.CreateRequest(code, type, jurisdiction, taxRegime, from, to, calculationType, params);
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/statutory-rules")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), StatutoryRuleDtos.Response.class);
    }

    private void activateRule(UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/payroll/statutory-rules/" + id + "/activate").with(jwt().authorities(APPROVE)))
                .andExpect(status().isOk());
    }

    private EmployeeCompensationDtos.ComponentRequest earning(String code, String amount) {
        return new EmployeeCompensationDtos.ComponentRequest(code, CompensationComponentType.EARNING, new BigDecimal(amount), null, null);
    }

    private EmployeeCompensationDtos.ComponentRequest deduction(String code, String amount) {
        return new EmployeeCompensationDtos.ComponentRequest(code, CompensationComponentType.DEDUCTION, new BigDecimal(amount), null, null);
    }

    /** {@code amount} is a NOT-NULL schema placeholder only - the wired rule computes the real contribution, never this value. */
    private EmployeeCompensationDtos.ComponentRequest deductionWithRule(String code, String ruleCode) {
        return new EmployeeCompensationDtos.ComponentRequest(code, CompensationComponentType.DEDUCTION, BigDecimal.ZERO, null, ruleCode);
    }

    private EmployeeCompensationDtos.Response createCompensation(UUID employeeRef, LocalDate from,
                                                                   List<EmployeeCompensationDtos.ComponentRequest> components) throws Exception {
        var request = new EmployeeCompensationDtos.CreateRequest(employeeRef, from, null, components);
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/compensations")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), EmployeeCompensationDtos.Response.class);
    }

    private void upsertStatutoryProfile(UUID employeeRef, StatutoryProfileDtos.UpsertRequest request) throws Exception {
        mockMvc.perform(put("/api/v1/payroll/statutory-profiles/" + employeeRef)
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /** APPLICABLE with every identifier present - keeps Compensation Management's own exceptions out of these rule-engine-focused tests. */
    private void fullyApplicableStatutoryProfile(UUID employeeRef) throws Exception {
        fullyApplicableStatutoryProfile(employeeRef, null);
    }

    private void fullyApplicableStatutoryProfile(UUID employeeRef, String taxRegime) throws Exception {
        upsertStatutoryProfile(employeeRef, new StatutoryProfileDtos.UpsertRequest(
                StatutoryApplicabilityStatus.APPLICABLE, "100123456789", "MEMBER-1", LocalDate.of(2000, 1, 1), null,
                StatutoryApplicabilityStatus.APPLICABLE, "ESI-MEMBER-1", LocalDate.of(2000, 1, 1), null,
                StatutoryApplicabilityStatus.APPLICABLE, "Karnataka", LocalDate.of(2000, 1, 1), null, taxRegime));
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

    private PayrollRunDtos.Response createRun(UUID periodId) throws Exception {
        var request = new PayrollRunDtos.CreateRequest(periodId);
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/runs")
                        .with(jwt().authorities(PROCESS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PayrollRunDtos.Response.class);
    }

    private void processRun(UUID runId) throws Exception {
        mockMvc.perform(post("/api/v1/payroll/runs/" + runId + "/process").with(jwt().authorities(PROCESS)))
                .andExpect(status().isOk());
    }

    private List<PayrollException> exceptionsFor(UUID runId, UUID employeeRef) {
        return exceptionRepository.findAll().stream()
                .filter(e -> e.getRunId().equals(runId) && e.getEmployeeRef().equals(employeeRef))
                .toList();
    }

    private BigDecimal pfFinalAmount(PayrollRunLine line) throws Exception {
        return componentFinalAmount(line, "PF");
    }

    private BigDecimal componentFinalAmount(PayrollRunLine line, String componentCode) throws Exception {
        List<Map<String, Object>> breakdown = objectMapper.readValue(line.getComponentBreakdown(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
        for (Map<String, Object> entry : breakdown) {
            if (componentCode.equals(entry.get("componentCode"))) {
                return new BigDecimal(String.valueOf(entry.get("finalAmount")));
            }
        }
        throw new IllegalStateException("Component " + componentCode + " not found in breakdown.");
    }
}
