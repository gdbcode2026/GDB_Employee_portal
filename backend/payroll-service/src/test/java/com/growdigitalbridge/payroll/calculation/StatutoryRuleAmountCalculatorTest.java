package com.growdigitalbridge.payroll.calculation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.EmployeeCompensation;
import com.growdigitalbridge.payroll.domain.PayFrequency;
import com.growdigitalbridge.payroll.domain.PayrollPeriod;
import com.growdigitalbridge.payroll.domain.StatutoryRule;
import com.growdigitalbridge.payroll.domain.StatutoryRuleCalculationType;
import com.growdigitalbridge.payroll.domain.StatutoryRuleParameters;
import com.growdigitalbridge.payroll.domain.StatutoryRuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure arithmetic tests for {@link StatutoryRuleAmountCalculator} - no Spring context, mirroring
 * {@code EmployeeCompensationServiceTest}'s established unit-test style. Every amount used here
 * is synthetic TEST DATA, never a real PF/ESI/Professional Tax/TDS rate.
 */
class StatutoryRuleAmountCalculatorTest {

    private final StatutoryRuleAmountCalculator calculator = new StatutoryRuleAmountCalculator(new ObjectMapper());

    private PayrollPeriod period() {
        return new PayrollPeriod(UUID.randomUUID(), 2031, 1, LocalDate.of(2031, 1, 1), LocalDate.of(2031, 1, 31),
                LocalDate.of(2031, 1, 25), "system", Instant.now());
    }

    private EmployeeCompensation compensation() {
        return new EmployeeCompensation(UUID.randomUUID(), UUID.randomUUID(), "INR", PayFrequency.MONTHLY,
                LocalDate.of(2031, 1, 1), null, "hr-1", Instant.now());
    }

    private CompensationComponent component(UUID compensationId, String code, CompensationComponentType type, String amount, String strategyCode) {
        return new CompensationComponent(UUID.randomUUID(), compensationId, code, type, new BigDecimal(amount), null, strategyCode, "hr-1", Instant.now());
    }

    private StatutoryRule rule(StatutoryRuleCalculationType type, StatutoryRuleParameters params) {
        String json = calculator.encode(params);
        return new StatutoryRule(UUID.randomUUID(), "TEST_RULE", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), null, type, json, "hr-1", Instant.now());
    }

    private ComponentCalculationContext context(EmployeeCompensation compensation, CompensationComponent component, List<CompensationComponent> all) {
        return new ComponentCalculationContext(period(), compensation, component, all, List.of(), List.of());
    }

    @Test
    void fixedAmountReturnsTheConfiguredAmountUnchanged() {
        StatutoryRule rule = rule(StatutoryRuleCalculationType.FIXED_AMOUNT,
                new StatutoryRuleParameters(new BigDecimal("500.00"), null, null, null, null, null));
        EmployeeCompensation compensation = compensation();
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");

        BigDecimal amount = calculator.compute(rule, context(compensation, pf, List.of(pf)));

        assertThat(amount).isEqualByComparingTo("500.00");
    }

    @Test
    void percentageWithNoNamedBasisUsesSumOfEarningComponents() {
        StatutoryRule rule = rule(StatutoryRuleCalculationType.PERCENTAGE,
                new StatutoryRuleParameters(null, new BigDecimal("10"), null, null, null, null));
        EmployeeCompensation compensation = compensation();
        CompensationComponent basic = component(compensation.getId(), "BASIC_SALARY", CompensationComponentType.EARNING, "40000.00", null);
        CompensationComponent hra = component(compensation.getId(), "HRA", CompensationComponentType.EARNING, "10000.00", null);
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");
        List<CompensationComponent> all = List.of(basic, hra, pf);

        BigDecimal amount = calculator.compute(rule, context(compensation, pf, all));

        // 10% of (40000 + 10000) = 5000.00
        assertThat(amount).isEqualByComparingTo("5000.00");
    }

    @Test
    void percentageWithANamedBasisComponentIgnoresOtherEarnings() {
        StatutoryRule rule = rule(StatutoryRuleCalculationType.PERCENTAGE,
                new StatutoryRuleParameters(null, new BigDecimal("12"), "BASIC_SALARY", null, null, null));
        EmployeeCompensation compensation = compensation();
        CompensationComponent basic = component(compensation.getId(), "BASIC_SALARY", CompensationComponentType.EARNING, "40000.00", null);
        CompensationComponent hra = component(compensation.getId(), "HRA", CompensationComponentType.EARNING, "10000.00", null);
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");
        List<CompensationComponent> all = List.of(basic, hra, pf);

        BigDecimal amount = calculator.compute(rule, context(compensation, pf, all));

        // 12% of 40000 (BASIC_SALARY only) = 4800.00, HRA ignored.
        assertThat(amount).isEqualByComparingTo("4800.00");
    }

    @Test
    void thresholdBasedCapsTheWageBasisBeforeApplyingPercentage() {
        StatutoryRule rule = rule(StatutoryRuleCalculationType.THRESHOLD_BASED,
                new StatutoryRuleParameters(null, new BigDecimal("10"), null, null, new BigDecimal("15000.00"), null));
        EmployeeCompensation compensation = compensation();
        CompensationComponent basic = component(compensation.getId(), "BASIC_SALARY", CompensationComponentType.EARNING, "40000.00", null);
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");
        List<CompensationComponent> all = List.of(basic, pf);

        BigDecimal amount = calculator.compute(rule, context(compensation, pf, all));

        // Wage basis is capped at maxWage=15000 before the 10% is applied: 10% of 15000 = 1500.00.
        assertThat(amount).isEqualByComparingTo("1500.00");
    }

    @Test
    void thresholdBasedClampsTheFinalAmountToTheConfiguredCap() {
        StatutoryRule rule = rule(StatutoryRuleCalculationType.THRESHOLD_BASED,
                new StatutoryRuleParameters(null, new BigDecimal("50"), null, null, null, new BigDecimal("1000.00")));
        EmployeeCompensation compensation = compensation();
        CompensationComponent basic = component(compensation.getId(), "BASIC_SALARY", CompensationComponentType.EARNING, "40000.00", null);
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");
        List<CompensationComponent> all = List.of(basic, pf);

        BigDecimal amount = calculator.compute(rule, context(compensation, pf, all));

        // 50% of 40000 = 20000, clamped down to the configured cap of 1000.00.
        assertThat(amount).isEqualByComparingTo("1000.00");
    }

    @Test
    void isEligibleIsAlwaysTrueForFixedAmountAndPercentageRules() {
        StatutoryRule fixed = rule(StatutoryRuleCalculationType.FIXED_AMOUNT,
                new StatutoryRuleParameters(new BigDecimal("1.00"), null, null, null, null, null));
        StatutoryRule percentage = rule(StatutoryRuleCalculationType.PERCENTAGE,
                new StatutoryRuleParameters(null, new BigDecimal("1"), null, null, null, null));
        EmployeeCompensation compensation = compensation();
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");
        ComponentCalculationContext ctx = context(compensation, pf, List.of(pf));

        assertThat(calculator.isEligible(fixed, ctx)).isTrue();
        assertThat(calculator.isEligible(percentage, ctx)).isTrue();
    }

    @Test
    void isEligibleIsFalseWhenWageBasisFallsBelowTheConfiguredMinWage() {
        StatutoryRule rule = rule(StatutoryRuleCalculationType.THRESHOLD_BASED,
                new StatutoryRuleParameters(null, new BigDecimal("10"), null, new BigDecimal("25000.00"), null, null));
        EmployeeCompensation compensation = compensation();
        CompensationComponent basic = component(compensation.getId(), "BASIC_SALARY", CompensationComponentType.EARNING, "20000.00", null);
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");
        List<CompensationComponent> all = List.of(basic, pf);

        assertThat(calculator.isEligible(rule, context(compensation, pf, all))).isFalse();
    }

    @Test
    void isEligibleIsTrueWhenWageBasisMeetsTheConfiguredMinWage() {
        StatutoryRule rule = rule(StatutoryRuleCalculationType.THRESHOLD_BASED,
                new StatutoryRuleParameters(null, new BigDecimal("10"), null, new BigDecimal("25000.00"), null, null));
        EmployeeCompensation compensation = compensation();
        CompensationComponent basic = component(compensation.getId(), "BASIC_SALARY", CompensationComponentType.EARNING, "30000.00", null);
        CompensationComponent pf = component(compensation.getId(), "PF", CompensationComponentType.DEDUCTION, "0.00", "TEST_RULE");
        List<CompensationComponent> all = List.of(basic, pf);

        assertThat(calculator.isEligible(rule, context(compensation, pf, all))).isTrue();
    }
}
