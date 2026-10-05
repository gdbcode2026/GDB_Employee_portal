package com.growdigitalbridge.payroll.calculation;

import com.growdigitalbridge.payroll.domain.StatutoryRule;
import com.growdigitalbridge.payroll.repository.StatutoryRuleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves "the {@code ACTIVE} {@link StatutoryRule} version applicable to this
 * code/jurisdiction/tax-regime for this date" (Rule Engine task, item 7/9; {@code taxRegime}
 * added by the India Payroll V1 architecture-extension task) - mirroring {@link
 * CompensationResolver}'s role for compensation. Exactly one match resolves; zero or more than
 * one (the latter should never occur once {@code StatutoryRuleService.activate} enforces
 * no-overlap, but is handled defensively rather than guessed) both resolve to "no usable rule,"
 * which {@code StatutoryRuleCalculationStrategy} turns into a {@link MissingStatutoryRuleException}.
 */
@Component
public class StatutoryRuleResolver {

    private final StatutoryRuleRepository repository;

    public StatutoryRuleResolver(StatutoryRuleRepository repository) {
        this.repository = repository;
    }

    public Optional<StatutoryRule> resolveActive(String code, String jurisdiction, String taxRegime, LocalDate date) {
        List<StatutoryRule> matches = repository.findActiveCovering(code, jurisdiction, taxRegime, date);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }
}
