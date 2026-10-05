package com.growdigitalbridge.payroll.calculation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.StatutoryRule;
import com.growdigitalbridge.payroll.domain.StatutoryRuleParameters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Pure arithmetic over a {@link StatutoryRule}'s {@code calculationType}/{@code parameters} -
 * no PF/ESI/Professional Tax/TDS rate, slab, or threshold is hard-coded here; every number this
 * class touches comes from the rule's own configured {@link StatutoryRuleParameters} (item 2/17).
 * Shared by {@code StatutoryRuleCalculationStrategy} (computation) and {@code
 * StatutoryRuleService} (encode/decode for the management API), so the JSON shape is defined and
 * parsed in exactly one place (item 9: "no duplication of calculation logic").
 */
@Component
public class StatutoryRuleAmountCalculator {

    private final ObjectMapper objectMapper;

    public StatutoryRuleAmountCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * {@code THRESHOLD_BASED} rules only: {@code false} when a configured {@code minWage} floor
     * exists and the resolved wage basis falls below it - the rule is correctly inapplicable, not
     * missing. {@code FIXED_AMOUNT}/{@code PERCENTAGE} rules have no eligibility concept and are
     * always eligible.
     */
    public boolean isEligible(StatutoryRule rule, ComponentCalculationContext context) {
        if (rule.getCalculationType() != com.growdigitalbridge.payroll.domain.StatutoryRuleCalculationType.THRESHOLD_BASED) {
            return true;
        }
        StatutoryRuleParameters params = decode(rule.getParameters());
        if (params.minWage() == null) {
            return true;
        }
        return wageBasis(params, context).compareTo(params.minWage()) >= 0;
    }

    /** Only called once {@link #isEligible} is true - the caller treats ineligibility separately, never as zero-by-formula. */
    public BigDecimal compute(StatutoryRule rule, ComponentCalculationContext context) {
        StatutoryRuleParameters params = decode(rule.getParameters());
        return switch (rule.getCalculationType()) {
            case FIXED_AMOUNT -> params.amount() == null ? BigDecimal.ZERO : params.amount();
            case PERCENTAGE -> percentageOf(wageBasis(params, context), params.percentage());
            case THRESHOLD_BASED -> thresholdBased(params, context);
        };
    }

    private BigDecimal thresholdBased(StatutoryRuleParameters params, ComponentCalculationContext context) {
        BigDecimal basis = wageBasis(params, context);
        BigDecimal cappedBasis = params.maxWage() != null && basis.compareTo(params.maxWage()) > 0 ? params.maxWage() : basis;
        BigDecimal amount = percentageOf(cappedBasis, params.percentage());
        return params.cap() != null && amount.compareTo(params.cap()) > 0 ? params.cap() : amount;
    }

    private BigDecimal percentageOf(BigDecimal basis, BigDecimal percentage) {
        if (percentage == null) {
            return BigDecimal.ZERO;
        }
        return basis.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Named basis component if configured, else the sum of every EARNING component's configured amount ("gross"). */
    private BigDecimal wageBasis(StatutoryRuleParameters params, ComponentCalculationContext context) {
        if (params.wageBasisComponentCode() != null && !params.wageBasisComponentCode().isBlank()) {
            return context.allComponents().stream()
                    .filter(c -> params.wageBasisComponentCode().equals(c.getComponentCode()))
                    .map(CompensationComponent::getAmount)
                    .findFirst().orElse(BigDecimal.ZERO);
        }
        return context.allComponents().stream()
                .filter(c -> c.getComponentType() == CompensationComponentType.EARNING)
                .map(CompensationComponent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public StatutoryRuleParameters decode(String json) {
        try {
            return objectMapper.readValue(json, StatutoryRuleParameters.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse statutory rule parameters.", e);
        }
    }

    public String encode(StatutoryRuleParameters parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize statutory rule parameters.", e);
        }
    }
}
