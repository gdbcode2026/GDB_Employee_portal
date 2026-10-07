package com.growdigitalbridge.expense.service;

import com.growdigitalbridge.expense.api.dto.Decision;
import com.growdigitalbridge.expense.api.dto.ExpenseClaimDtos;
import com.growdigitalbridge.expense.api.dto.PageResponse;
import com.growdigitalbridge.expense.domain.ExpenseClaim;
import com.growdigitalbridge.expense.domain.ExpenseClaimStatus;
import com.growdigitalbridge.expense.domain.ExpenseLine;
import com.growdigitalbridge.expense.domain.ReceiptReference;
import com.growdigitalbridge.expense.repository.ExpenseClaimRepository;
import com.growdigitalbridge.expense.repository.ExpenseLineRepository;
import com.growdigitalbridge.expense.repository.ReceiptReferenceRepository;
import com.growdigitalbridge.expense.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.expense.service.exception.InvalidRequestException;
import com.growdigitalbridge.expense.service.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns expense claims end to end: draft authoring, submission, direct in-domain decisions
 * (mirroring Leave Service's own {@code POST .../decisions} shape), reimbursement, and the
 * application of an optional {@code workflow.completed.v1} outcome for a claim an
 * administrator chose to route through Workflow Service instead (mirroring Asset Service's
 * identical treatment of AssetRequest). {@code total} is always the server-computed sum of the
 * claim's lines - never independently client-supplied - so no claim/line-total reconciliation
 * rule needs to be invented. There is no documented dedicated cancel endpoint, so a
 * self-directed DRAFT-to-CANCELLED transition is accepted only through the same {@code PATCH}
 * update endpoint used for ordinary draft edits (mirroring Performance Service's Goal
 * {@code UpdateRequest}, which carries an optional status field for the same reason).
 */
@Service
public class ExpenseClaimService {

    private final ExpenseClaimRepository claimRepository;
    private final ExpenseLineRepository lineRepository;
    private final ReceiptReferenceRepository receiptRepository;
    private final ExpenseAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public ExpenseClaimService(ExpenseClaimRepository claimRepository, ExpenseLineRepository lineRepository,
                                ReceiptReferenceRepository receiptRepository, ExpenseAccessGuard accessGuard,
                                OutboxEventWriter outboxEventWriter) {
        this.claimRepository = claimRepository;
        this.lineRepository = lineRepository;
        this.receiptRepository = receiptRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public ExpenseClaimDtos.Response create(Authentication authentication, ExpenseClaimDtos.CreateRequest request, String actor) {
        UUID employeeRef = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        Instant now = Instant.now();
        BigDecimal total = sumLines(request.lines());
        ExpenseClaim claim = new ExpenseClaim(UUID.randomUUID(), employeeRef, request.currency(), total, actor, now);
        claimRepository.save(claim);
        saveLines(claim.getId(), request.lines(), actor, now);
        saveReceipts(claim.getId(), request.receipts(), actor, now);
        return toResponse(claim);
    }

    @Transactional(readOnly = true)
    public ExpenseClaimDtos.Response getById(UUID id, Authentication authentication) {
        ExpenseClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim " + id + " was not found."));
        if (!canView(authentication, claim)) {
            throw new ResourceNotFoundException("Expense claim " + id + " was not found.");
        }
        return toResponse(claim);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseClaimDtos.Response> list(Authentication authentication, UUID employeeId, ExpenseClaimStatus status,
                                                          LocalDate from, LocalDate to, Pageable pageable) {
        ExpenseAccessGuard.ListScope scope = accessGuard.resolveListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing expense claims requires self, team, or all read scope.");
        }
        Page<ExpenseClaim> page;
        if (scope.unrestricted()) {
            page = employeeId == null
                    ? claimRepository.searchAll(status, from, to, pageable)
                    : claimRepository.searchWithinScope(List.of(employeeId), status, from, to, pageable);
        } else if (scope.allowedIds().isEmpty() || (employeeId != null && !scope.allowedIds().contains(employeeId))) {
            page = Page.empty(pageable);
        } else {
            var effectiveIds = employeeId != null ? List.of(employeeId) : List.copyOf(scope.allowedIds());
            page = claimRepository.searchWithinScope(effectiveIds, status, from, to, pageable);
        }
        // Reporting V1 authorization review, Part A (docs/REPORTING_AUTHORIZATION_REVIEW.md):
        // taken directly from the guard's own tier decision, never from the query result above.
        PageResponse.ResponseScope responseScope = toResponseScope(scope.tier());
        return PageResponse.of(page.map(this::toResponse), responseScope);
    }

    private PageResponse.ResponseScope toResponseScope(ExpenseAccessGuard.ListScope.Tier tier) {
        return switch (tier) {
            case SELF -> PageResponse.ResponseScope.SELF;
            case TEAM -> PageResponse.ResponseScope.TEAM;
            case ALL -> PageResponse.ResponseScope.ALL;
        };
    }

    @Transactional
    public ExpenseClaimDtos.Response update(UUID id, Authentication authentication, ExpenseClaimDtos.UpdateRequest request, String actor) {
        ExpenseClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim " + id + " was not found."));
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (!claim.getEmployeeRef().equals(self)) {
            throw new ResourceNotFoundException("Expense claim " + id + " was not found.");
        }
        if (claim.getStatus() != ExpenseClaimStatus.DRAFT) {
            throw new InvalidLifecycleTransitionException("Expense claim " + id + " can no longer be edited.");
        }

        Instant now = Instant.now();
        if (request.status() != null) {
            if (request.status() != ExpenseClaimStatus.CANCELLED) {
                throw new InvalidRequestException("A draft claim may only be self-cancelled through this endpoint.");
            }
            claim.cancel(actor, now);
            return toResponse(claim);
        }

        String currency = request.currency() != null ? request.currency() : claim.getCurrency();
        if (request.lines() != null) {
            BigDecimal total = sumLines(request.lines());
            lineRepository.deleteByClaimId(id);
            saveLines(id, request.lines(), actor, now);
            claim.updateDraft(currency, total, actor, now);
        } else if (request.currency() != null) {
            claim.updateDraft(currency, claim.getTotal(), actor, now);
        }
        if (request.receipts() != null) {
            receiptRepository.deleteByClaimId(id);
            saveReceipts(id, request.receipts(), actor, now);
        }
        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimDtos.Response submit(UUID id, Authentication authentication, String actor, UUID correlationId) {
        ExpenseClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim " + id + " was not found."));
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        if (!claim.getEmployeeRef().equals(self)) {
            throw new ResourceNotFoundException("Expense claim " + id + " was not found.");
        }
        if (claim.getStatus() != ExpenseClaimStatus.DRAFT) {
            throw new InvalidLifecycleTransitionException("Expense claim " + id + " is not in draft state.");
        }
        Instant now = Instant.now();
        claim.submit(actor, now);

        outboxEventWriter.write("expense.submitted.v1", claim.getId(), Map.of(
                "claimId", claim.getId().toString(),
                "employeeId", claim.getEmployeeRef().toString(),
                "currency", claim.getCurrency(),
                "total", claim.getTotal().toString()), correlationId);

        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimDtos.Response decide(UUID id, Authentication authentication, ExpenseClaimDtos.DecisionRequest request,
                                             String actor, UUID correlationId) {
        ExpenseClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim " + id + " was not found."));
        if (!accessGuard.canActOnBehalfOf(authentication, claim.getEmployeeRef(), "expense.approve.team", "expense.approve.all")) {
            throw new ResourceNotFoundException("Expense claim " + id + " was not found.");
        }
        if (claim.getStatus() != ExpenseClaimStatus.SUBMITTED) {
            throw new InvalidLifecycleTransitionException("Expense claim " + id + " has already been decided.");
        }
        Instant now = Instant.now();
        if (request.decision() == Decision.APPROVED) {
            claim.decide(ExpenseClaimStatus.APPROVED, claim.getWorkflowRef(), actor, now);
            publishApproved(claim, correlationId);
        } else {
            claim.decide(ExpenseClaimStatus.REJECTED, claim.getWorkflowRef(), actor, now);
        }
        return toResponse(claim);
    }

    @Transactional
    public ExpenseClaimDtos.Response reimburse(UUID id, String actor) {
        ExpenseClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim " + id + " was not found."));
        if (claim.getStatus() != ExpenseClaimStatus.APPROVED) {
            throw new InvalidLifecycleTransitionException("Expense claim " + id + " is not approved.");
        }
        claim.reimburse(actor, Instant.now());
        return toResponse(claim);
    }

    /**
     * Applies a terminal {@code workflow.completed.v1} outcome. Idempotent both via the
     * caller's inbox check and this "only while still SUBMITTED" state guard. Only
     * {@code APPROVED}/{@code REJECTED}/{@code CANCELLED} have a corresponding {@link
     * ExpenseClaimStatus}; an outcome of {@code RUNNING} or {@code EXPIRED} has no documented
     * ExpenseClaim state to map to (DATABASE.md's state list has no EXPIRED state, unlike
     * Workflow's own InstanceStatus), so such an outcome is intentionally left unapplied rather
     * than inventing a new state.
     */
    @Transactional
    public void applyWorkflowOutcome(UUID claimId, UUID workflowId, String outcome, String actor, Instant now, UUID correlationId) {
        claimRepository.findById(claimId).ifPresent(claim -> {
            if (claim.getStatus() != ExpenseClaimStatus.SUBMITTED) {
                return;
            }
            switch (outcome) {
                case "APPROVED" -> {
                    claim.decide(ExpenseClaimStatus.APPROVED, workflowId, actor, now);
                    publishApproved(claim, correlationId);
                }
                case "REJECTED" -> claim.decide(ExpenseClaimStatus.REJECTED, workflowId, actor, now);
                case "CANCELLED" -> claim.decide(ExpenseClaimStatus.CANCELLED, workflowId, actor, now);
                default -> { /* RUNNING/EXPIRED: no corresponding documented ExpenseClaim state - left unapplied. */ }
            }
        });
    }

    private void publishApproved(ExpenseClaim claim, UUID correlationId) {
        outboxEventWriter.write("expense.approved.v1", claim.getId(), Map.of(
                "claimId", claim.getId().toString(),
                "employeeId", claim.getEmployeeRef().toString(),
                "approvedTotal", claim.getTotal().toString(),
                "currency", claim.getCurrency()), correlationId);
    }

    private boolean canView(Authentication authentication, ExpenseClaim claim) {
        if (accessGuard.hasAuthority(authentication, "expense.read.all")) {
            return true;
        }
        if (accessGuard.hasAuthority(authentication, "expense.read.team")
                && accessGuard.isWithinCallersTeamScope(authentication, claim.getEmployeeRef())) {
            return true;
        }
        if (accessGuard.hasAuthority(authentication, "expense.read.self")) {
            return accessGuard.resolveSelf(authentication).map(self -> self.equals(claim.getEmployeeRef())).orElse(false);
        }
        return false;
    }

    private BigDecimal sumLines(List<ExpenseClaimDtos.LineItem> lines) {
        return lines.stream().map(ExpenseClaimDtos.LineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void saveLines(UUID claimId, List<ExpenseClaimDtos.LineItem> lines, String actor, Instant now) {
        lines.forEach(line -> lineRepository.save(new ExpenseLine(UUID.randomUUID(), claimId, line.date(), line.category(),
                line.amount(), line.description(), actor, now)));
    }

    private void saveReceipts(UUID claimId, List<ExpenseClaimDtos.ReceiptRef> receipts, String actor, Instant now) {
        if (receipts == null) {
            return;
        }
        receipts.forEach(receipt -> receiptRepository.save(new ReceiptReference(UUID.randomUUID(), claimId, receipt.documentRef(), actor, now)));
    }

    private ExpenseClaimDtos.Response toResponse(ExpenseClaim claim) {
        List<ExpenseClaimDtos.LineItem> lines = lineRepository.findByClaimId(claim.getId()).stream()
                .map(line -> new ExpenseClaimDtos.LineItem(line.getDate(), line.getCategory(), line.getAmount(), line.getDescription()))
                .toList();
        List<ExpenseClaimDtos.ReceiptRef> receipts = receiptRepository.findByClaimId(claim.getId()).stream()
                .map(receipt -> new ExpenseClaimDtos.ReceiptRef(receipt.getDocumentRef()))
                .toList();
        return new ExpenseClaimDtos.Response(claim.getId(), claim.getEmployeeRef(), claim.getCurrency(), claim.getTotal(),
                claim.getStatus(), claim.getWorkflowRef(), lines, receipts, claim.getDecidedBy(), claim.getDecidedAt(),
                claim.getCreatedAt(), claim.getUpdatedAt());
    }
}
