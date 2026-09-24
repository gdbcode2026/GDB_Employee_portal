package com.growdigitalbridge.expense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single line item on an {@link ExpenseClaim}. {@code category} is freeform text - no
 * expense-category vocabulary is documented anywhere in this repository, and none is invented
 * here. Lines are replaced wholesale on each draft update rather than edited in place - no
 * separate line-management endpoint is documented in API.md.
 */
@Entity
@Table(name = "expense_lines")
public class ExpenseLine {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "line_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 128, updatable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 128)
    private String updatedBy;

    @Version
    private long version;

    protected ExpenseLine() { }

    public ExpenseLine(UUID id, UUID claimId, LocalDate date, String category, BigDecimal amount, String description,
                        String actor, Instant now) {
        this.id = id;
        this.claimId = claimId;
        this.date = date;
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public LocalDate getDate() { return date; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
