package com.growdigitalbridge.payroll.calculation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.domain.CompensationComponent;
import com.growdigitalbridge.payroll.domain.CompensationComponentType;
import com.growdigitalbridge.payroll.domain.StatutoryRule;
import com.growdigitalbridge.payroll.domain.StatutoryRuleBracket;
import com.growdigitalbridge.payroll.domain.StatutoryRuleParameters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
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
            case SLAB_BASED -> slabBased(params, context);
            case PROGRESSIVE_TAX -> progressiveTax(params, context);
        };
    }

    private BigDecimal thresholdBased(StatutoryRuleParameters params, ComponentCalculationContext context) {
        BigDecimal basis = wageBasis(params, context);
        BigDecimal cappedBasis = params.maxWage() != null && basis.compareTo(params.maxWage()) > 0 ? params.maxWage() : basis;
        BigDecimal amount = percentageOf(cappedBasis, params.percentage());
        return params.cap() != null && amount.compareTo(params.cap()) > 0 ? params.cap() : amount;
    }

    /**
     * {@code SLAB_BASED}: exactly ONE bracket applies - the one whose {@code [lowerBound,
     * upperBound)} range contains the wage basis - contributing that bracket's own {@code
     * fixedAmount} or {@code percentage} of the (whole) basis. Never cumulative; fits a flat-fee
     * slab tax such as Professional Tax. A basis below the first bracket's {@code lowerBound}
     * matches no bracket and correctly contributes zero - this is not an error, since {@code
     * StatutoryRuleService} already guarantees brackets are contiguous from write time, so "no
     * match" only happens below the very first bracket's floor.
     */
    private BigDecimal slabBased(StatutoryRuleParameters params, ComponentCalculationContext context) {
        BigDecimal basis = wageBasis(params, context);
        StatutoryRuleBracket matched = findMatchingBracket(params.brackets(), basis);
        if (matched == null) {
            return BigDecimal.ZERO;
        }
        return matched.fixedAmount() != null ? matched.fixedAmount() : percentageOf(basis, matched.percentage());
    }

    /**
     * {@code PROGRESSIVE_TAX}: every bracket the basis reaches contributes its own share - the
     * classic marginal-rate accumulation an income-tax calculation requires. Each bracket taxes
     * only the portion of the basis falling within its own {@code [lowerBound, upperBound)} span
     * (clipped to the basis for the final, partially-filled bracket), and the contributions are
     * summed. A {@code fixedAmount} bracket (if ever configured) contributes that flat amount in
     * full once the basis reaches it, rather than being scaled by span - included for
     * completeness of the generic shape, though a real income-tax bracket is ordinarily
     * percentage-based.
     */
    private BigDecimal progressiveTax(StatutoryRuleParameters params, ComponentCalculationContext context) {
        BigDecimal basis = wageBasis(params, context);
        BigDecimal total = BigDecimal.ZERO;
        for (StatutoryRuleBracket bracket : sortedByOrder(params.brackets())) {
            if (basis.compareTo(bracket.lowerBound()) <= 0) {
                break;
            }
            BigDecimal bracketTop = bracket.upperBound() == null ? basis : bracket.upperBound().min(basis);
            BigDecimal span = bracketTop.subtract(bracket.lowerBound());
            if (span.signum() <= 0) {
                continue;
            }
            total = total.add(bracket.fixedAmount() != null ? bracket.fixedAmount() : percentageOf(span, bracket.percentage()));
        }
        return total;
    }

    private StatutoryRuleBracket findMatchingBracket(List<StatutoryRuleBracket> brackets, BigDecimal basis) {
        for (StatutoryRuleBracket bracket : sortedByOrder(brackets)) {
            boolean atOrAboveLower = basis.compareTo(bracket.lowerBound()) >= 0;
            boolean belowUpper = bracket.upperBound() == null || basis.compareTo(bracket.upperBound()) < 0;
            if (atOrAboveLower && belowUpper) {
                return bracket;
            }
        }
        return null;
    }

    private List<StatutoryRuleBracket> sortedByOrder(List<StatutoryRuleBracket> brackets) {
        return brackets == null ? List.of() : brackets.stream().sorted(Comparator.comparingInt(StatutoryRuleBracket::order)).toList();
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
