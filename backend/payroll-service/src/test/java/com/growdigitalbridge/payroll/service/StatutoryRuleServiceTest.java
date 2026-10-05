package com.growdigitalbridge.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growdigitalbridge.payroll.api.dto.StatutoryRuleDtos;
import com.growdigitalbridge.payroll.calculation.StatutoryRuleAmountCalculator;
import com.growdigitalbridge.payroll.domain.StatutoryRule;
import com.growdigitalbridge.payroll.domain.StatutoryRuleCalculationType;
import com.growdigitalbridge.payroll.domain.StatutoryRuleType;
import com.growdigitalbridge.payroll.repository.StatutoryRuleRepository;
import com.growdigitalbridge.payroll.service.exception.ConflictException;
import com.growdigitalbridge.payroll.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.payroll.service.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link StatutoryRuleService}'s versioning/validation/overlap logic (Rule
 * Engine task, item 7/8), mirroring {@code EmployeeCompensationServiceTest}'s established style.
 * {@link StatutoryRuleAmountCalculator} is used as a real instance (it is a pure JSON codec here,
 * no database/network access) rather than mocked.
 */
@ExtendWith(MockitoExtension.class)
class StatutoryRuleServiceTest {

    @Mock private StatutoryRuleRepository repository;
    @Mock private PayrollAuditLog auditLog;

    private final StatutoryRuleAmountCalculator amountCalculator = new StatutoryRuleAmountCalculator(new ObjectMapper());

    private StatutoryRuleService service() {
        return new StatutoryRuleService(repository, amountCalculator, auditLog);
    }

    private StatutoryRuleDtos.ParametersRequest fixedAmountParams(String amount) {
        return new StatutoryRuleDtos.ParametersRequest(new BigDecimal(amount), null, null, null, null, null, null);
    }

    private StatutoryRuleDtos.CreateRequest createRequest(String code, StatutoryRuleType type, String jurisdiction,
                                                            LocalDate from, LocalDate to) {
        return new StatutoryRuleDtos.CreateRequest(code, type, jurisdiction, null, from, to,
                StatutoryRuleCalculationType.FIXED_AMOUNT, fixedAmountParams("100.00"));
    }

    private StatutoryRule existingRule(String code, StatutoryRuleType type, String jurisdiction, int ruleVersion,
                                        LocalDate from, LocalDate to, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus status) {
        StatutoryRule rule = new StatutoryRule(UUID.randomUUID(), code, type, jurisdiction, null, ruleVersion, from, to,
                StatutoryRuleCalculationType.FIXED_AMOUNT, amountCalculator.encode(
                        new com.growdigitalbridge.payroll.domain.StatutoryRuleParameters(new BigDecimal("100.00"), null, null, null, null, null, null)),
                "hr-1", Instant.now());
        if (status == com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE) {
            rule.activate("hr-1", Instant.now());
        } else if (status == com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.INACTIVE) {
            rule.activate("hr-1", Instant.now());
            rule.deactivate("hr-1", Instant.now());
        }
        return rule;
    }

    @Test
    void createFirstVersionOfANewCodeStartsAtVersionOne() {
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PF")).thenReturn(List.of());

        StatutoryRuleDtos.Response response = service().create(
                createRequest("TEST_PF", StatutoryRuleType.PF, null, LocalDate.of(2031, 1, 1), null), "hr-1", null);

        assertThat(response.ruleVersion()).isEqualTo(1);
    }

    @Test
    void createNextVersionOfAnExistingCodeIncrementsTheVersionNumber() {
        StatutoryRule v1 = existingRule("TEST_PF", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), LocalDate.of(2031, 6, 30), com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE);
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PF")).thenReturn(List.of(v1));

        StatutoryRuleDtos.Response response = service().create(
                createRequest("TEST_PF", StatutoryRuleType.PF, null, LocalDate.of(2031, 7, 1), null), "hr-1", null);

        assertThat(response.ruleVersion()).isEqualTo(2);
    }

    @Test
    void createRejectsAMismatchedRuleTypeForAnExistingCode() {
        StatutoryRule v1 = existingRule("TEST_X", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.DRAFT);
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_X")).thenReturn(List.of(v1));

        assertThatThrownBy(() -> service().create(
                createRequest("TEST_X", StatutoryRuleType.ESI, null, LocalDate.of(2031, 7, 1), null), "hr-1", null))
                .isInstanceOf(InvalidRequestException.class);
    }

    /**
     * A different jurisdiction under the same {@code code} never conflicts with an existing
     * family - it starts its own independent version-1 sequence (Section G/item 5: one
     * {@code calculationStrategyCode} must resolve a different version per employee based on
     * their own jurisdiction, so jurisdiction cannot be locked per code the way {@code ruleType}
     * is).
     */
    @Test
    void createStartsAnIndependentVersionSequenceForADifferentJurisdictionUnderTheSameCode() {
        StatutoryRule karnatakaV1 = existingRule("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.DRAFT);
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PT")).thenReturn(List.of(karnatakaV1));

        StatutoryRuleDtos.Response maharashtraV1 = service().create(
                createRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Maharashtra", LocalDate.of(2031, 7, 1), null), "hr-1", null);

        assertThat(maharashtraV1.ruleVersion()).isEqualTo(1);
    }

    @Test
    void createRejectsAMismatchedRuleTypeEvenAcrossDifferentJurisdictionsOfTheSameCode() {
        StatutoryRule karnatakaV1 = existingRule("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.DRAFT);
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PT")).thenReturn(List.of(karnatakaV1));

        assertThatThrownBy(() -> service().create(
                createRequest("TEST_PT", StatutoryRuleType.ESI, "Maharashtra", LocalDate.of(2031, 7, 1), null), "hr-1", null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsEffectiveToBeforeEffectiveFrom() {
        assertThatThrownBy(() -> service().create(
                createRequest("TEST_PF", StatutoryRuleType.PF, null, LocalDate.of(2031, 6, 1), LocalDate.of(2031, 1, 1)), "hr-1", null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsAFixedAmountRuleWithNoAmount() {
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PF", StatutoryRuleType.PF, null, null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.FIXED_AMOUNT,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsAPercentageRuleWithNoPercentage() {
        var request = new StatutoryRuleDtos.CreateRequest("TEST_ESI", StatutoryRuleType.ESI, null, null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.PERCENTAGE,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsAThresholdRuleWithMinWageGreaterThanMaxWage() {
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PF", StatutoryRuleType.PF, null, null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.THRESHOLD_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, new BigDecimal("10"), null,
                        new BigDecimal("30000"), new BigDecimal("15000"), null, null));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsSlabBasedRuleWithNoBrackets() {
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsABracketWithBothFixedAmountAndPercentageSet() {
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), null, new BigDecimal("100"), new BigDecimal("10")));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsABracketWithNeitherFixedAmountNorPercentageSet() {
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), null, null, null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsDuplicateBracketOrders() {
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), new BigDecimal("100"), new BigDecimal("10"), null),
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("100"), null, new BigDecimal("20"), null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsANonLastBracketWithAnOpenEndedUpperBound() {
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), null, new BigDecimal("10"), null),
                new StatutoryRuleDtos.BracketRequest(2, new BigDecimal("100"), null, new BigDecimal("20"), null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsOverlappingBrackets() {
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), new BigDecimal("100"), new BigDecimal("10"), null),
                new StatutoryRuleDtos.BracketRequest(2, new BigDecimal("50"), null, new BigDecimal("20"), null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsBracketsWithAGapBetweenThem() {
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), new BigDecimal("100"), new BigDecimal("10"), null),
                new StatutoryRuleDtos.BracketRequest(2, new BigDecimal("150"), null, new BigDecimal("20"), null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatThrownBy(() -> service().create(request, "hr-1", null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createAcceptsAValidContiguousBracketSequenceForSlabBased() {
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PT")).thenReturn(List.of());
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), new BigDecimal("21000"), new BigDecimal("0"), null),
                new StatutoryRuleDtos.BracketRequest(2, new BigDecimal("21000"), new BigDecimal("30000"), new BigDecimal("180"), null),
                new StatutoryRuleDtos.BracketRequest(3, new BigDecimal("30000"), null, new BigDecimal("425"), null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatCode(() -> service().create(request, "hr-1", null)).doesNotThrowAnyException();
    }

    @Test
    void createAcceptsAZeroFixedAmountBracketAsALegitimateNilSlab() {
        // A bracket with fixedAmount=0 is a legitimate "Nil" slab (e.g. Professional Tax's
        // below-threshold bracket) and must be accepted - this guards that it is NOT rejected by
        // the exactly-one-of-fixedAmount-or-percentage check merely because the value is zero.
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PT")).thenReturn(List.of());
        var brackets = List.of(
                new StatutoryRuleDtos.BracketRequest(1, new BigDecimal("0"), new BigDecimal("21000"), new BigDecimal("0"), null),
                new StatutoryRuleDtos.BracketRequest(2, new BigDecimal("21000"), null, new BigDecimal("180"), null));
        var request = new StatutoryRuleDtos.CreateRequest("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", null,
                LocalDate.of(2031, 1, 1), null, StatutoryRuleCalculationType.SLAB_BASED,
                new StatutoryRuleDtos.ParametersRequest(null, null, null, null, null, null, brackets));

        assertThatCode(() -> service().create(request, "hr-1", null)).doesNotThrowAnyException();
    }

    @Test
    void updateDraftRejectedWhenRuleIsNotInDraftStatus() {
        StatutoryRule active = existingRule("TEST_PF", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE);
        when(repository.findById(active.getId())).thenReturn(java.util.Optional.of(active));

        var update = new StatutoryRuleDtos.UpdateRequest(LocalDate.of(2031, 1, 1), null,
                StatutoryRuleCalculationType.FIXED_AMOUNT, fixedAmountParams("200.00"));

        assertThatThrownBy(() -> service().updateDraft(active.getId(), update, "hr-1", null))
                .isInstanceOf(InvalidLifecycleTransitionException.class);
    }

    @Test
    void activateRejectedWhenRuleIsNotInDraftStatus() {
        StatutoryRule active = existingRule("TEST_PF", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE);
        when(repository.findById(active.getId())).thenReturn(java.util.Optional.of(active));

        assertThatThrownBy(() -> service().activate(active.getId(), "checker-1", null))
                .isInstanceOf(InvalidLifecycleTransitionException.class);
    }

    @Test
    void activateRejectsAnOverlapWithAnotherActiveVersionOfTheSameCodeAndJurisdiction() {
        StatutoryRule activeV1 = existingRule("TEST_PF", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE);
        StatutoryRule draftV2 = existingRule("TEST_PF", StatutoryRuleType.PF, null, 2,
                LocalDate.of(2031, 6, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.DRAFT);
        when(repository.findById(draftV2.getId())).thenReturn(java.util.Optional.of(draftV2));
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PF")).thenReturn(List.of(draftV2, activeV1));

        assertThatThrownBy(() -> service().activate(draftV2.getId(), "checker-1", null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void activateAllowsASequentialNonOverlappingVersion() {
        StatutoryRule activeV1 = existingRule("TEST_PF", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), LocalDate.of(2031, 5, 31), com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE);
        StatutoryRule draftV2 = existingRule("TEST_PF", StatutoryRuleType.PF, null, 2,
                LocalDate.of(2031, 6, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.DRAFT);
        when(repository.findById(draftV2.getId())).thenReturn(java.util.Optional.of(draftV2));
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PF")).thenReturn(List.of(draftV2, activeV1));

        assertThatCode(() -> service().activate(draftV2.getId(), "checker-1", null)).doesNotThrowAnyException();
    }

    @Test
    void activateNeverConflictsAcrossDifferentJurisdictionsOfTheSameCode() {
        StatutoryRule activeKarnataka = existingRule("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Karnataka", 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.ACTIVE);
        StatutoryRule draftMaharashtra = existingRule("TEST_PT", StatutoryRuleType.PROFESSIONAL_TAX, "Maharashtra", 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.DRAFT);
        when(repository.findById(draftMaharashtra.getId())).thenReturn(java.util.Optional.of(draftMaharashtra));
        when(repository.findByCodeOrderByRuleVersionDesc("TEST_PT")).thenReturn(List.of(activeKarnataka, draftMaharashtra));

        assertThatCode(() -> service().activate(draftMaharashtra.getId(), "checker-1", null)).doesNotThrowAnyException();
    }

    @Test
    void deactivateRejectedWhenRuleIsNotActive() {
        StatutoryRule draft = existingRule("TEST_PF", StatutoryRuleType.PF, null, 1,
                LocalDate.of(2031, 1, 1), null, com.growdigitalbridge.payroll.domain.StatutoryRuleStatus.DRAFT);
        when(repository.findById(draft.getId())).thenReturn(java.util.Optional.of(draft));

        assertThatThrownBy(() -> service().deactivate(draft.getId(), "checker-1", null))
                .isInstanceOf(InvalidLifecycleTransitionException.class);
    }
}
