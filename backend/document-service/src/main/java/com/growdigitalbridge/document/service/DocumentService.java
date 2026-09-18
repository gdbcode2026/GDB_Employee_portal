package com.growdigitalbridge.document.service;

import com.growdigitalbridge.document.api.dto.DocumentDtos;
import com.growdigitalbridge.document.domain.Document;
import com.growdigitalbridge.document.domain.DocumentStatus;
import com.growdigitalbridge.document.domain.DocumentVersion;
import com.growdigitalbridge.document.domain.ScanStatus;
import com.growdigitalbridge.document.repository.DocumentRepository;
import com.growdigitalbridge.document.repository.DocumentVersionRepository;
import com.growdigitalbridge.document.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.document.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns document metadata and its upload lifecycle. Per API.md this is deliberately
 * "metadata/scan completion only": no bytes are ever received, stored, or served here, and no
 * malware scan is actually performed - both an object storage provider and a malware-scanning
 * provider are undecided (docs/ARCHITECTURE_REVIEW.md item 3). {@code complete} validates the
 * declared checksum instead (the one concrete, documented validation: "validate type/size/
 * checksum and quarantine state"), quarantining on mismatch rather than assuming success.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public DocumentService(DocumentRepository repository, DocumentVersionRepository versionRepository,
                            DocumentAccessGuard accessGuard, OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
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

    @Transactional(readOnly = true)
    public DocumentDtos.Response getById(UUID id, Authentication authentication) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " was not found."));
        if (!accessGuard.canView(authentication, document.getOwnerRef())) {
            throw new ResourceNotFoundException("Document " + id + " was not found.");
        }
        return toResponse(document, latestVersion(id));
    }

    @Transactional
    public DocumentDtos.Response complete(UUID id, Authentication authentication, DocumentDtos.CompleteRequest request,
                                           String actor, UUID correlationId) {
        Document document = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + id + " was not found."));
        if (!accessGuard.canComplete(authentication, document.getOwnerRef())) {
            throw new ResourceNotFoundException("Document " + id + " was not found.");
        }
        DocumentVersion version = latestVersion(id);
        if (version.getScanStatus() != ScanStatus.PENDING) {
            throw new InvalidLifecycleTransitionException("Document " + id + " has already been completed.");
        }

        Instant now = Instant.now();
        boolean checksumMatches = version.getChecksum().equals(request.checksum());
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
        return new DocumentDtos.DownloadResponse(document.getId(), version.getObjectKey(), version.getChecksum(),
                version.getMimeType(), version.getSizeBytes());
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
