package com.growdigitalbridge.payroll.calculation;

import com.growdigitalbridge.payroll.domain.EmployeeStatutoryProfile;
import com.growdigitalbridge.payroll.domain.StatutoryRule;
import com.growdigitalbridge.payroll.repository.EmployeeStatutoryProfileRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Resolves and computes a component's amount from a versioned {@link StatutoryRule} rather than
 * a fixed configured value (Rule Engine task) - registered in {@link CalculationStrategyRegistry}
 * for any {@code calculationStrategyCode} other than {@code null}/{@code FIXED_AMOUNT}. The code
 * itself *is* the {@link StatutoryRule#getCode()} to resolve: which rule family applies to a
 * component is data on the component, never a branch in this class (the same configuration-driven
 * pattern {@code FixedAmountStrategy} already established).
 *
 * <p>Jurisdiction-aware resolution (Professional Tax, item 5) is the one place this class reads
 * the component's code directly: only a {@code PROFESSIONAL_TAX}-coded component looks up the
 * employee's {@code EmployeeStatutoryProfile.ptJurisdiction} to select the matching jurisdiction's
 * rule version; every other component resolves a jurisdiction-less (national) rule.
 *
 * <p>Never throws out to the API layer - {@code PayrollCalculationEngine} catches both {@link
 * MissingStatutoryRuleException} and {@link StatutoryRuleNotApplicableException} per component
 * and turns each into a {@code PayrollException} instead of failing the employee's line or the
 * run (item 10).
 */
@Component
public class StatutoryRuleCalculationStrategy implements StatutoryCalculator, TaxCalculator {

    private static final String PROFESSIONAL_TAX_COMPONENT_CODE = "PROFESSIONAL_TAX";

    private final StatutoryRuleResolver resolver;
    private final StatutoryRuleAmountCalculator amountCalculator;
    private final EmployeeStatutoryProfileRepository statutoryProfileRepository;

    public StatutoryRuleCalculationStrategy(StatutoryRuleResolver resolver, StatutoryRuleAmountCalculator amountCalculator,
                                             EmployeeStatutoryProfileRepository statutoryProfileRepository) {
        this.resolver = resolver;
        this.amountCalculator = amountCalculator;
        this.statutoryProfileRepository = statutoryProfileRepository;
    }

    @Override
    public BigDecimal computeAmount(ComponentCalculationContext context) {
        String code = context.component().getCalculationStrategyCode();
        String jurisdiction = jurisdictionFor(context);
        StatutoryRule rule = resolver.resolveActive(code, jurisdiction, context.period().getStartDate())
                .orElseThrow(() -> new MissingStatutoryRuleException(code));
        if (!amountCalculator.isEligible(rule, context)) {
            throw new StatutoryRuleNotApplicableException(code);
        }
        return amountCalculator.compute(rule, context);
    }

    private String jurisdictionFor(ComponentCalculationContext context) {
        if (!PROFESSIONAL_TAX_COMPONENT_CODE.equals(context.component().getComponentCode())) {
            return null;
        }
        return statutoryProfileRepository.findByEmployeeRef(context.compensation().getEmployeeRef())
                .map(EmployeeStatutoryProfile::getPtJurisdiction)
                .orElse(null);
    }
}
