package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.PageResponse;
import com.growdigitalbridge.payroll.api.dto.StatutoryRuleDtos;
import com.growdigitalbridge.payroll.calculation.StatutoryRuleAmountCalculator;
import com.growdigitalbridge.payroll.domain.StatutoryRule;
import com.growdigitalbridge.payroll.domain.StatutoryRuleBracket;
import com.growdigitalbridge.payroll.domain.StatutoryRuleCalculationType;
import com.growdigitalbridge.payroll.domain.StatutoryRuleParameters;
import com.growdigitalbridge.payroll.domain.StatutoryRuleStatus;
import com.growdigitalbridge.payroll.repository.StatutoryRuleRepository;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.payroll.service.exception.InvalidRequestException;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages versioned, effective-dated {@link StatutoryRule}s (Rule Engine task, item 1/7/8). Never
 * accepts a real PF/ESI/Professional Tax/TDS rate, slab, or threshold as "approved" - this class
 * only validates *structure* (effective dates, parameter shape for the chosen {@code
 * calculationType}, no overlapping active versions); whether a submitted value is legally correct
 * remains outside this codebase's authority (PENDING_GDB_APPROVAL, PAYROLL_REQUIREMENTS.md
 * Section X).
 *
 * <p><b>Versioning</b> (item 7): creating a rule with a {@code code} that does not exist yet
 * starts version 1; creating again with the same {@code code} creates the next version. {@code
 * ruleType} is locked for the whole {@code code} family across every jurisdiction (a code names
 * one conceptual rule, e.g. "this is a Professional Tax rule"). {@code jurisdiction}, by
 * contrast, gets its OWN independent version-1-onward sequence under the same {@code code} - this
 * is what lets one {@code calculationStrategyCode} on a component resolve a different, correct
 * version per employee based on their own {@code EmployeeStatutoryProfile.ptJurisdiction} (item
 * 5), rather than requiring a separate code per jurisdiction.
 *
 * <p><b>Immutability</b> (item 7/8): only a {@code DRAFT} version can be edited ({@link
 * #updateDraft}); {@code ACTIVE}/{@code INACTIVE} versions are never mutated - correcting one
 * means creating the next version, which is what keeps a historical payroll calculation
 * reproducible after a newer version is later activated.
 *
 * <p><b>Overlap/validation</b> (item 8): {@link #activate} rejects (409) a version that would
 * overlap another {@code ACTIVE} version of the same {@code code}+{@code jurisdiction} - the
 * write-time guard that keeps {@code StatutoryRuleResolver} from ever finding more than one
 * match, mirroring {@code EmployeeCompensationService}'s existing overlap-prevention precedent.
 * Two rules sharing a {@code code} but differing only in {@code jurisdiction} never conflict
 * (Professional Tax, item 5/8 "conflicting jurisdiction rules" is scoped per jurisdiction).
 */
@Service
public class StatutoryRuleService {

    private final StatutoryRuleRepository repository;
    private final StatutoryRuleAmountCalculator amountCalculator;
    private final PayrollAuditLog auditLog;

    public StatutoryRuleService(StatutoryRuleRepository repository, StatutoryRuleAmountCalculator amountCalculator,
                                 PayrollAuditLog auditLog) {
        this.repository = repository;
        this.amountCalculator = amountCalculator;
        this.auditLog = auditLog;
    }

    @Transactional
    public StatutoryRuleDtos.Response create(StatutoryRuleDtos.CreateRequest request, String actor, UUID correlationId) {
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());
        validateParameters(request.calculationType(), request.parameters());

        List<StatutoryRule> existingVersions = repository.findByCodeOrderByRuleVersionDesc(request.code());
        if (existingVersions.stream().anyMatch(r -> r.getRuleType() != request.ruleType())) {
            throw new InvalidRequestException("Rule code '" + request.code() + "' is already defined as rule type "
                    + existingVersions.get(0).getRuleType() + " and cannot be redefined as " + request.ruleType() + ".");
        }
        // Version numbering is scoped per (code, jurisdiction, taxRegime) - a jurisdiction-aware
        // rule family (e.g. Professional Tax, item 5) or tax-regime-aware family (TDS, India
        // Payroll V1 architecture-extension task) resolves the SAME code differently per employee
        // based on their profile's jurisdiction/regime (StatutoryRuleCalculationStrategy), so
        // each (jurisdiction, taxRegime) pair under one code gets its own independent
        // version-1-onward sequence, never locked to the first one ever created under that code.
        List<StatutoryRule> sameFamily = existingVersions.stream()
                .filter(r -> Objects.equals(r.getJurisdiction(), request.jurisdiction())
                        && Objects.equals(r.getTaxRegime(), request.taxRegime()))
                .toList();
        int nextVersion = sameFamily.isEmpty() ? 1 : sameFamily.get(0).getRuleVersion() + 1;

        Instant now = Instant.now();
        String parametersJson = amountCalculator.encode(toParameters(request.parameters()));
        StatutoryRule rule = new StatutoryRule(UUID.randomUUID(), request.code(), request.ruleType(), request.jurisdiction(),
                request.taxRegime(), nextVersion, request.effectiveFrom(), request.effectiveTo(), request.calculationType(),
                parametersJson, actor, now);
        repository.save(rule);
        auditLog.statutoryRuleCreated(rule.getId(), rule.getCode(), rule.getRuleVersion(), actor, correlationId);
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public StatutoryRuleDtos.Response getById(UUID id, String actor, UUID correlationId) {
        StatutoryRule rule = find(id);
        auditLog.sensitiveRead("statutory_rule", id, actor, correlationId);
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public PageResponse<StatutoryRuleDtos.Response> list(String code, Pageable pageable) {
        Page<StatutoryRule> page = (code == null || code.isBlank())
                ? repository.findAllByOrderByCodeAscRuleVersionDesc(pageable)
                : repository.findByCodeOrderByRuleVersionDesc(code, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public StatutoryRuleDtos.Response updateDraft(UUID id, StatutoryRuleDtos.UpdateRequest request, String actor, UUID correlationId) {
        StatutoryRule rule = find(id);
        if (rule.getStatus() != StatutoryRuleStatus.DRAFT) {
            throw new InvalidLifecycleTransitionException("Statutory rule " + id
                    + " is not in DRAFT status and cannot be edited; create a new version instead.");
        }
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());
        validateParameters(request.calculationType(), request.parameters());

        String parametersJson = amountCalculator.encode(toParameters(request.parameters()));
        rule.updateDraft(request.effectiveFrom(), request.effectiveTo(), request.calculationType(), parametersJson, actor, Instant.now());
        repository.save(rule);
        auditLog.statutoryRuleUpdated(rule.getId(), actor, correlationId);
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleDtos.Response activate(UUID id, String actor, UUID correlationId) {
        StatutoryRule rule = find(id);
        if (rule.getStatus() != StatutoryRuleStatus.DRAFT) {
            throw new InvalidLifecycleTransitionException("Statutory rule " + id + " must be in DRAFT status to activate.");
        }
        assertNoOverlapWithActiveVersions(rule);
        rule.activate(actor, Instant.now());
        repository.save(rule);
        auditLog.statutoryRuleActivated(rule.getId(), rule.getCode(), rule.getRuleVersion(), actor, correlationId);
        return toResponse(rule);
    }

    @Transactional
    public StatutoryRuleDtos.Response deactivate(UUID id, String actor, UUID correlationId) {
        StatutoryRule rule = find(id);
        if (rule.getStatus() != StatutoryRuleStatus.ACTIVE) {
            throw new InvalidLifecycleTransitionException("Statutory rule " + id + " is not ACTIVE and cannot be deactivated.");
        }
        rule.deactivate(actor, Instant.now());
        repository.save(rule);
        auditLog.statutoryRuleDeactivated(rule.getId(), rule.getCode(), rule.getRuleVersion(), actor, correlationId);
        return toResponse(rule);
    }

    private void validateEffectiveDates(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new InvalidRequestException("effectiveTo must not be before effectiveFrom.");
        }
    }

    /** Structural validation only (item 8) - never a legal-value judgment. */
    private void validateParameters(StatutoryRuleCalculationType type, StatutoryRuleDtos.ParametersRequest params) {
        switch (type) {
            case FIXED_AMOUNT -> {
                if (params.amount() == null || params.amount().signum() < 0) {
                    throw new InvalidRequestException("FIXED_AMOUNT rules require a non-negative amount.");
                }
            }
            case PERCENTAGE, THRESHOLD_BASED -> {
                if (params.percentage() == null) {
                    throw new InvalidRequestException(type + " rules require a percentage.");
                }
                if (params.minWage() != null && params.maxWage() != null && params.minWage().compareTo(params.maxWage()) > 0) {
                    throw new InvalidRequestException("minWage must not be greater than maxWage.");
                }
            }
            case SLAB_BASED, PROGRESSIVE_TAX -> validateBrackets(params.brackets());
        }
    }

    /**
     * Structural validation of an ordered bracket list (India Payroll V1 architecture-extension
     * task, item "invalid/overlapping slabs are rejected"/"bracket ordering is deterministic"):
     * no duplicate {@code order} values, exactly one of {@code fixedAmount}/{@code percentage}
     * per bracket, only the last bracket (by {@code order}) may leave {@code upperBound} open,
     * and every bracket's {@code lowerBound} must exactly equal the previous bracket's {@code
     * upperBound} - contiguous and non-overlapping by construction, never a gap or an overlap.
     */
    private void validateBrackets(List<StatutoryRuleDtos.BracketRequest> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            throw new InvalidRequestException("SLAB_BASED/PROGRESSIVE_TAX rules require at least one bracket.");
        }
        Set<Integer> seenOrders = new HashSet<>();
        for (StatutoryRuleDtos.BracketRequest bracket : brackets) {
            if (!seenOrders.add(bracket.order())) {
                throw new InvalidRequestException("Duplicate bracket order: " + bracket.order() + ".");
            }
            boolean hasFixed = bracket.fixedAmount() != null;
            boolean hasPercentage = bracket.percentage() != null;
            if (hasFixed == hasPercentage) {
                throw new InvalidRequestException("Bracket " + bracket.order() + " requires exactly one of fixedAmount or percentage.");
            }
        }
        List<StatutoryRuleDtos.BracketRequest> ordered = brackets.stream()
                .sorted(Comparator.comparing(StatutoryRuleDtos.BracketRequest::order))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            StatutoryRuleDtos.BracketRequest bracket = ordered.get(i);
            boolean isLast = i == ordered.size() - 1;
            if (!isLast && bracket.upperBound() == null) {
                throw new InvalidRequestException("Only the last bracket (by order) may have an open-ended upperBound.");
            }
            if (bracket.upperBound() != null && bracket.upperBound().compareTo(bracket.lowerBound()) <= 0) {
                throw new InvalidRequestException("Bracket " + bracket.order() + "'s upperBound must be greater than its lowerBound.");
            }
            if (i > 0) {
                StatutoryRuleDtos.BracketRequest previous = ordered.get(i - 1);
                if (previous.upperBound() == null || bracket.lowerBound().compareTo(previous.upperBound()) != 0) {
                    throw new InvalidRequestException("Brackets must be contiguous and non-overlapping: bracket " + bracket.order()
                            + "'s lowerBound must equal the previous bracket's upperBound.");
                }
            }
        }
    }

    /**
     * Rejects activation when this version's effective range would overlap another {@code
     * ACTIVE} version of the same {@code code}+{@code jurisdiction}+{@code taxRegime} (item 8).
     * A different jurisdiction or tax regime under the same {@code code} never conflicts.
     */
    private void assertNoOverlapWithActiveVersions(StatutoryRule rule) {
        for (StatutoryRule other : repository.findByCodeOrderByRuleVersionDesc(rule.getCode())) {
            if (other.getId().equals(rule.getId()) || other.getStatus() != StatutoryRuleStatus.ACTIVE) {
                continue;
            }
            if (!Objects.equals(other.getJurisdiction(), rule.getJurisdiction())
                    || !Objects.equals(other.getTaxRegime(), rule.getTaxRegime())) {
                continue;
            }
            boolean disjoint = (rule.getEffectiveTo() != null && rule.getEffectiveTo().isBefore(other.getEffectiveFrom()))
                    || (other.getEffectiveTo() != null && other.getEffectiveTo().isBefore(rule.getEffectiveFrom()));
            if (!disjoint) {
                throw new ConflictException("Activating version " + rule.getRuleVersion() + " of '" + rule.getCode()
                        + "' would overlap already-active version " + other.getRuleVersion() + " for the same jurisdiction/tax regime.");
            }
        }
    }

    private StatutoryRule find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Statutory rule " + id + " was not found."));
    }

    private StatutoryRuleParameters toParameters(StatutoryRuleDtos.ParametersRequest p) {
        return new StatutoryRuleParameters(p.amount(), p.percentage(), p.wageBasisComponentCode(), p.minWage(), p.maxWage(), p.cap(),
                toBrackets(p.brackets()));
    }

    private List<StatutoryRuleBracket> toBrackets(List<StatutoryRuleDtos.BracketRequest> brackets) {
        if (brackets == null) {
            return null;
        }
        return brackets.stream()
                .map(b -> new StatutoryRuleBracket(b.order(), b.lowerBound(), b.upperBound(), b.fixedAmount(), b.percentage()))
                .toList();
    }

    private List<StatutoryRuleDtos.BracketRequest> toBracketRequests(List<StatutoryRuleBracket> brackets) {
        if (brackets == null) {
            return null;
        }
        return brackets.stream()
                .map(b -> new StatutoryRuleDtos.BracketRequest(b.order(), b.lowerBound(), b.upperBound(), b.fixedAmount(), b.percentage()))
                .toList();
    }

    private StatutoryRuleDtos.Response toResponse(StatutoryRule rule) {
        StatutoryRuleParameters params = amountCalculator.decode(rule.getParameters());
        StatutoryRuleDtos.ParametersRequest paramsDto = new StatutoryRuleDtos.ParametersRequest(
                params.amount(), params.percentage(), params.wageBasisComponentCode(),
                params.minWage(), params.maxWage(), params.cap(), toBracketRequests(params.brackets()));
        return new StatutoryRuleDtos.Response(rule.getId(), rule.getCode(), rule.getRuleType(), rule.getJurisdiction(), rule.getTaxRegime(),
                rule.getRuleVersion(), rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.getStatus(), rule.getCalculationType(),
                paramsDto, rule.getCreatedAt(), rule.getUpdatedAt());
    }
}
