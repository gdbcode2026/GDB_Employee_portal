package com.growdigitalbridge.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Document metadata only - binary bytes are never stored here (MICROSERVICES.md: "No binary
 * files in database"). {@code ownerRef} is a cross-service reference to Employee's own
 * primary key - never an enforced database FK - and is always resolved server-side from the
 * caller's validated identity, never supplied by a client as an authorization input.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "owner_ref", nullable = false)
    private UUID ownerRef;

    @Column(length = 200)
    private String classification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status;

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

    protected Document() { }

    public Document(UUID id, UUID ownerRef, String classification, String actor, Instant now) {
        this.id = id;
        this.ownerRef = ownerRef;
        this.classification = classification;
        this.status = DocumentStatus.PENDING_SCAN;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void changeStatus(DocumentStatus status, String actor, Instant now) {
        this.status = status;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOwnerRef() { return ownerRef; }
    public String getClassification() { return classification; }
    public DocumentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
