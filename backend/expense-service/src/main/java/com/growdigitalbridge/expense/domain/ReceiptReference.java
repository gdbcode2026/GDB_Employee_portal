package com.growdigitalbridge.expense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code documentRef} is an opaque cross-service reference to a Document Service record -
 * never an enforced database FK, per DATABASE.md's {@code *_ref} convention - and is never
 * synchronously validated against Document Service (no such requirement is documented).
 * Replaced wholesale on each draft update, like {@link ExpenseLine}.
 */
@Entity
@Table(name = "receipt_references")
public class ReceiptReference {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "document_ref", nullable = false)
    private UUID documentRef;

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

    protected ReceiptReference() { }

    public ReceiptReference(UUID id, UUID claimId, UUID documentRef, String actor, Instant now) {
        this.id = id;
        this.claimId = claimId;
        this.documentRef = documentRef;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public UUID getDocumentRef() { return documentRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
