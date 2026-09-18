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
 * {@code objectKey} is a server-generated opaque reference only - no object storage provider
 * is integrated (none is decided per docs/ARCHITECTURE_REVIEW.md item 3), so it identifies
 * where a future storage integration would resolve the bytes, not an actual stored file.
 */
@Entity
@Table(name = "document_versions")
public class DocumentVersion {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "object_key", nullable = false, length = 200)
    private String objectKey;

    @Column(nullable = false, length = 128)
    private String checksum;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 16)
    private ScanStatus scanStatus;

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

    protected DocumentVersion() { }

    public DocumentVersion(UUID id, UUID documentId, int versionNumber, String objectKey, String checksum,
                            String mimeType, long sizeBytes, String actor, Instant now) {
        this.id = id;
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.objectKey = objectKey;
        this.checksum = checksum;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.scanStatus = ScanStatus.PENDING;
        this.createdAt = now;
        this.createdBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void markScanResult(ScanStatus scanStatus, String actor, Instant now) {
        this.scanStatus = scanStatus;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public int getVersionNumber() { return versionNumber; }
    public String getObjectKey() { return objectKey; }
    public String getChecksum() { return checksum; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public ScanStatus getScanStatus() { return scanStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
