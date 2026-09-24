package com.growdigitalbridge.expense.api.dto;

import com.growdigitalbridge.expense.domain.ExpenseClaimStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ExpenseClaimDtos {

    private ExpenseClaimDtos() { }

    /** {@code category} is deliberately freeform - no expense-category vocabulary is documented anywhere in this repository. */
    public record LineItem(@NotNull LocalDate date, @NotBlank @Size(max = 120) String category,
                            @NotNull @DecimalMin(value = "0.01") BigDecimal amount, @Size(max = 2000) String description) { }

    public record ReceiptRef(@NotNull UUID documentRef) { }

    /** {@code currency} is deliberately unconstrained beyond basic shape - no currency allow-list or conversion policy is documented. */
    public record CreateRequest(@NotBlank @Size(max = 8) String currency, @NotEmpty @Valid List<LineItem> lines,
                                 List<@Valid ReceiptRef> receipts) { }

    /**
     * Partial update of a DRAFT claim. {@code status} is accepted only as a self-directed
     * {@code CANCELLED} transition (there is no documented dedicated cancel endpoint); any
     * other value is rejected by the service layer. Fields left null keep their current value,
     * except {@code lines}/{@code receipts}, which replace the full set when supplied.
     */
    public record UpdateRequest(@Size(max = 8) String currency, @Valid List<LineItem> lines,
                                 List<@Valid ReceiptRef> receipts, ExpenseClaimStatus status) { }

    public record DecisionRequest(@NotNull Decision decision, @Size(max = 2000) String comment) { }

    public record Response(UUID id, UUID employeeRef, String currency, BigDecimal total, ExpenseClaimStatus status,
                            UUID workflowRef, List<LineItem> lines, List<ReceiptRef> receipts, String decidedBy,
                            Instant decidedAt, Instant createdAt, Instant updatedAt) { }
}
