package com.growdigitalbridge.document.service;

import com.growdigitalbridge.document.api.dto.DocumentDtos;
import com.growdigitalbridge.document.domain.Document;
import com.growdigitalbridge.document.domain.DocumentStatus;
import com.growdigitalbridge.document.domain.DocumentVersion;
import com.growdigitalbridge.document.domain.ScanStatus;
import com.growdigitalbridge.document.repository.DocumentRepository;
import com.growdigitalbridge.document.repository.DocumentVersionRepository;
import com.growdigitalbridge.document.service.exception.ConflictException;
import com.growdigitalbridge.document.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.document.service.exception.InvalidRequestException;
import com.growdigitalbridge.document.service.exception.ResourceNotFoundException;
import com.growdigitalbridge.document.storage.ObjectStorageClient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns document metadata and its upload lifecycle. Per API.md this is "metadata/scan completion"
 * plus real binary storage: {@code uploadContent} is the only place actual file bytes are
 * received, and they are written straight through to {@link ObjectStorageClient} - never to
 * PostgreSQL (MICROSERVICES.md: "No binary files in database"). No malware-scanning provider is
 * integrated (docs/ARCHITECTURE_REVIEW.md item 3 leaves it undecided); {@code complete} validates
 * checksum integrity instead - against the real uploaded bytes when present, quarantining on any
 * mismatch, and falling back to the originally-declared checksum only when no content was ever
 * uploaded for this version (preserving every pre-existing metadata-only test/caller).
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectStorageClient objectStorageClient;

    public DocumentService(DocumentRepository repository, DocumentVersionRepository versionRepository,
                            DocumentAccessGuard accessGuard, OutboxEventWriter outboxEventWriter,
                            ObjectStorageClient objectStorageClient) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
        this.objectStorageClient = objectStorageClient;
    }

    @Transactional
    public DocumentDtos.Response upload(Authentication authentication, DocumentDtos.UploadRequest request, String actor) {
        UUID ownerRef = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        Instant now = Instant.now();
        Document document = new Document(UUID.randomUUID(), ownerRef, request.classification(), actor, now);
        repository.save(document);

        DocumentVersion version = new DocumentVersion(UUID.randomUUID(), document.getId(), 1, UUID.randomUUID().toString(),
                request.checksum(), request.mimeType(), request.sizeBytes(), actor, now);
        versionRepository.save(version);

        return toResponse(document, version);
    }

    /**
     * {@code POST /documents/workload-uploads}: an authorized backend workload (never a
     * relayed employee token, per {@code SecurityConfig}'s {@code workload.document.upload}
     * gate) creates a document on behalf of an explicitly supplied {@code ownerRef}. Reuses the
     * exact same {@code PENDING_SCAN} creation this constructor already performs for self-service
     * uploads - the workload must still call {@code POST /documents/uploads/{id}/complete} to
     * reach {@code AVAILABLE}/{@code QUARANTINED}, exactly like a self-service upload, so no
     * second storage/scan mechanism is introduced. Idempotent per {@code (ownerRef, checksum)}:
     * a duplicate submission is rejected as a conflict rather than creating a second document,
     * mirroring Attendance Service's own natural-idempotency-guard precedent.
     */
    @Transactional
    public DocumentDtos.Response uploadOnBehalf(DocumentDtos.WorkloadUploadRequest request, String actor) {
        repository.findByOwnerRefAndChecksum(request.ownerRef(), request.checksum()).ifPresent(existing -> {
            throw new ConflictException("A document for owner " + request.ownerRef() + " with this checksum already exists.");
        });

        Instant now = Instant.now();
        Document document = new Document(UUID.randomUUID(), request.ownerRef(), request.classification(), actor, now);
        repository.save(document);

        DocumentVersion version = new DocumentVersion(UUID.randomUUID(), document.getId(), 1, UUID.randomUUID().toString(),
                request.checksum(), request.mimeType(), request.sizeBytes(), actor, now);
        versionRepository.save(version);

        return toResponse(document, version);
    }

    @Transactional(readOnly = true)
    public DocumentDtos.Response getById(UUID id, Authentication authentication) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " was not found."));
        if (!accessGuard.canView(authentication, document.getOwnerRef())) {
            throw new ResourceNotFoundException("Document " + id + " was not found.");
        }
        return toResponse(document, latestVersion(id));
    }

    /**
     * The only place actual file bytes are received (item 4). Authorized identically to {@link
     * #complete} - only the uploader, the creating workload, or {@code document.manage} may
     * supply content for a version. Declared {@code mimeType}/{@code sizeBytes} from upload time
     * are validated against what was actually sent, then the bytes are written straight through
     * to {@link ObjectStorageClient} under the version's own object key - never persisted here.
     */
    @Transactional
    public DocumentDtos.Response uploadContent(UUID id, Authentication authentication, byte[] content,
                                                String contentType, String actor) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " was not found."));
        if (!accessGuard.canComplete(authentication, document.getOwnerRef(), actor, document.getCreatedBy())) {
            throw new ResourceNotFoundException("Document " + id + " was not found.");
        }
        DocumentVersion version = latestVersion(id);
        if (version.getScanStatus() != ScanStatus.PENDING) {
            throw new InvalidLifecycleTransitionException("Document " + id + " has already been completed; content can no longer be uploaded.");
        }
        if (contentType != null && !contentType.isBlank() && !baseType(contentType).equals(baseType(version.getMimeType()))) {
            throw new InvalidRequestException("Uploaded content type '" + contentType
                    + "' does not match the declared MIME type '" + version.getMimeType() + "'.");
        }
        if (content.length != version.getSizeBytes()) {
            throw new InvalidRequestException("Uploaded content size " + content.length
                    + " does not match the declared size " + version.getSizeBytes() + ".");
        }

        objectStorageClient.putObject(version.getObjectKey(), content, version.getMimeType());
        return toResponse(document, version);
    }

    /** Strips any {@code ;charset=...}/parameter suffix a caller's Content-Type header may carry. */
    private String baseType(String mimeType) {
        int separator = mimeType.indexOf(';');
        return (separator < 0 ? mimeType : mimeType.substring(0, separator)).trim();
    }

    @Transactional
    public DocumentDtos.Response complete(UUID id, Authentication authentication, DocumentDtos.CompleteRequest request,
                                           String actor, UUID correlationId) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " was not found."));
        if (!accessGuard.canComplete(authentication, document.getOwnerRef(), actor, document.getCreatedBy())) {
            throw new ResourceNotFoundException("Document " + id + " was not found.");
        }
        DocumentVersion version = latestVersion(id);
        if (version.getScanStatus() != ScanStatus.PENDING) {
            throw new InvalidLifecycleTransitionException("Document " + id + " has already been completed.");
        }

        Instant now = Instant.now();
        boolean declaredMatches = version.getChecksum().equals(request.checksum());
        boolean checksumMatches = declaredMatches && realBytesMatchDeclaredChecksum(version);
        ScanStatus scanStatus = checksumMatches ? ScanStatus.CLEAN : ScanStatus.QUARANTINED;
        DocumentStatus documentStatus = checksumMatches ? DocumentStatus.AVAILABLE : DocumentStatus.QUARANTINED;

        version.markScanResult(scanStatus, actor, now);
        document.changeStatus(documentStatus, actor, now);

        outboxEventWriter.write("document.uploaded.v1", document.getId(), Map.of(
                "documentId", document.getId().toString(),
                "ownerId", document.getOwnerRef().toString(),
                "classification", document.getClassification() == null ? "" : document.getClassification(),
                "scanStatus", scanStatus.name()), correlationId);

        return toResponse(document, version);
    }

    /**
     * When real bytes have been uploaded via {@link #uploadContent} for this version, their
     * actual SHA-256 must match the checksum declared at upload time - a genuine integrity check
     * against the real object, not just two client-supplied strings agreeing with each other. If
     * no object exists in storage yet (no {@code uploadContent} call was ever made for this
     * version), there is nothing real to verify, so this returns {@code true} and {@code
     * complete} falls back to its original declared-checksum-only comparison.
     */
    private boolean realBytesMatchDeclaredChecksum(DocumentVersion version) {
        Optional<byte[]> stored = objectStorageClient.getObject(version.getObjectKey());
        if (stored.isEmpty()) {
            return true;
        }
        return sha256Hex(stored.get()).equalsIgnoreCase(version.getChecksum());
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    @Transactional(readOnly = true)
    public DocumentDtos.DownloadResponse download(UUID id, Authentication authentication) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " was not found."));
        if (!accessGuard.canView(authentication, document.getOwnerRef())) {
            throw new ResourceNotFoundException("Document " + id + " was not found.");
        }
        if (document.getStatus() != DocumentStatus.AVAILABLE) {
            throw new InvalidLifecycleTransitionException("Document " + id + " is not available for download.");
        }
        DocumentVersion version = latestVersion(id);

        String downloadUrl = null;
        Instant expiresAt = null;
        try {
            ObjectStorageClient.SignedDownload signed = objectStorageClient.presignDownload(version.getObjectKey(), version.getMimeType());
            downloadUrl = signed.url();
            expiresAt = signed.expiresAt();
        } catch (RuntimeException e) {
            log.warn("Failed to generate a pre-signed download URL for document {}: {}", id, e.getMessage());
        }

        return new DocumentDtos.DownloadResponse(document.getId(), version.getObjectKey(), version.getChecksum(),
                version.getMimeType(), version.getSizeBytes(), downloadUrl, expiresAt);
    }

    private DocumentVersion latestVersion(UUID documentId) {
        return versionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + documentId + " has no versions."));
    }

    private DocumentDtos.Response toResponse(Document document, DocumentVersion version) {
        DocumentDtos.VersionSummary summary = version == null ? null : new DocumentDtos.VersionSummary(
                version.getId(), version.getVersionNumber(), version.getObjectKey(), version.getChecksum(),
                version.getMimeType(), version.getSizeBytes(), version.getScanStatus());
        return new DocumentDtos.Response(document.getId(), document.getOwnerRef(), document.getClassification(),
                document.getStatus(), summary, document.getCreatedAt(), document.getUpdatedAt());
    }
}
