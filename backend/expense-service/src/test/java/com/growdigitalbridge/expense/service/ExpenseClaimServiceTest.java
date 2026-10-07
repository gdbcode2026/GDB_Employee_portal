package com.growdigitalbridge.expense.service;

import com.growdigitalbridge.expense.api.dto.PageResponse;
import com.growdigitalbridge.expense.repository.ExpenseClaimRepository;
import com.growdigitalbridge.expense.repository.ExpenseLineRepository;
import com.growdigitalbridge.expense.repository.ReceiptReferenceRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Reporting V1 authorization review, Part A (docs/REPORTING_AUTHORIZATION_REVIEW.md): proves
 * {@code list()}'s reported {@code page.scope} is taken directly from {@link
 * ExpenseAccessGuard.ListScope}'s own tier decision - never from the returned claims or their
 * count - for every reachable tier, including the empty-result case for each.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseClaimServiceTest {

    @Mock private ExpenseClaimRepository claimRepository;
    @Mock private ExpenseLineRepository lineRepository;
    @Mock private ReceiptReferenceRepository receiptRepository;
    @Mock private ExpenseAccessGuard accessGuard;
    @Mock private OutboxEventWriter outboxEventWriter;
    @Mock private Authentication authentication;

    private ExpenseClaimService service() {
        return new ExpenseClaimService(claimRepository, lineRepository, receiptRepository, accessGuard, outboxEventWriter);
    }

    private Page<com.growdigitalbridge.expense.domain.ExpenseClaim> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @Test
    void listThrowsAccessDeniedWhenScopeIsDenied() {
        when(accessGuard.resolveListScope(authentication)).thenReturn(ExpenseAccessGuard.ListScope.denied());

        assertThatThrownBy(() -> service().list(authentication, null, null, null, null, Pageable.unpaged()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listReportsSelfScopeForASelfOnlyCaller() {
        UUID self = UUID.randomUUID();
        when(accessGuard.resolveListScope(authentication)).thenReturn(ExpenseAccessGuard.ListScope.restrictedToSelf(Set.of(self)));
        when(claimRepository.searchWithinScope(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        var page = service().list(authentication, null, null, null, null, Pageable.unpaged());

        assertThat(page.page().scope()).isEqualTo(PageResponse.ResponseScope.SELF);
    }

    @Test
    void listReportsTeamScopeForAManager() {
        UUID reportId = UUID.randomUUID();
        when(accessGuard.resolveListScope(authentication)).thenReturn(ExpenseAccessGuard.ListScope.restrictedToTeam(Set.of(reportId)));
        when(claimRepository.searchWithinScope(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        var page = service().list(authentication, null, null, null, null, Pageable.unpaged());

        assertThat(page.page().scope()).isEqualTo(PageResponse.ResponseScope.TEAM);
    }

    @Test
    void listReportsAllScopeForAnUnrestrictedCaller() {
        when(accessGuard.resolveListScope(authentication)).thenReturn(ExpenseAccessGuard.ListScope.all());
        when(claimRepository.searchAll(any(), any(), any(), any())).thenReturn(emptyPage());

        var page = service().list(authentication, null, null, null, null, Pageable.unpaged());

        assertThat(page.page().scope()).isEqualTo(PageResponse.ResponseScope.ALL);
    }

    /**
     * The exact scenario a content-based heuristic would get wrong (see
     * docs/REPORTING_AUTHORIZATION_REVIEW.md's rejection of that approach for D4): zero claims
     * match, for a genuinely unrestricted (Finance-tier) caller. Scope must still read ALL.
     */
    @Test
    void listReportsAllScopeEvenWhenNoClaimsMatch() {
        when(accessGuard.resolveListScope(authentication)).thenReturn(ExpenseAccessGuard.ListScope.all());
        when(claimRepository.searchAll(any(), any(), any(), any())).thenReturn(emptyPage());

        var page = service().list(authentication, null, null, null, null, Pageable.unpaged());

        assertThat(page.items()).isEmpty();
        assertThat(page.page().scope()).isEqualTo(PageResponse.ResponseScope.ALL);
    }

    @Test
    void listReportsSelfScopeEvenWhenTheCallerHasNoClaimsAtAll() {
        UUID self = UUID.randomUUID();
        when(accessGuard.resolveListScope(authentication)).thenReturn(ExpenseAccessGuard.ListScope.restrictedToSelf(Set.of(self)));
        when(claimRepository.searchWithinScope(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        var page = service().list(authentication, null, null, null, null, Pageable.unpaged());

        assertThat(page.items()).isEmpty();
        assertThat(page.page().scope()).isEqualTo(PageResponse.ResponseScope.SELF);
    }
}
