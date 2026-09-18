package com.growdigitalbridge.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Matches DATABASE.md's documented Document-owned entity ("grant document FK/subject/
 * permission/expiry") exactly. No endpoint in API.md creates or manages grants, so nothing
 * in this codebase writes or reads this table yet - it exists only so the schema matches
 * what DATABASE.md documents Document Service as owning. Building a grant management
 * endpoint here would be inventing a workflow that isn't specified.
 */
@Entity
@Table(name = "access_grants")
public class AccessGrant {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "subject_ref", nullable = false)
    private UUID subjectRef;

    @Column(nullable = false, length = 64)
    private String permission;

    @Column(name = "expires_at")
    private Instant expiresAt;

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

    protected AccessGrant() { }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public UUID getSubjectRef() { return subjectRef; }
    public String getPermission() { return permission; }
    public Instant getExpiresAt() { return expiresAt; }
}
