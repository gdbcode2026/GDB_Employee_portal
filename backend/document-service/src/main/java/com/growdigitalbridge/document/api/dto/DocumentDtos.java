package com.growdigitalbridge.document.api.dto;

import com.growdigitalbridge.document.domain.DocumentStatus;
import com.growdigitalbridge.document.domain.ScanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() { }

    /**
     * No file-type allow-list or size limit is documented anywhere in this repository (see
     * docs/ARCHITECTURE_REVIEW.md item 3), so none is enforced beyond required-field presence.
     */
    public record UploadRequest(
            @Size(max = 200) String classification,
            @NotBlank @Size(max = 100) String mimeType,
            @NotNull @Positive Long sizeBytes,
            @NotBlank @Size(max = 128) String checksum) { }

    public record CompleteRequest(@NotBlank @Size(max = 128) String checksum) { }

    /**
     * For {@code POST /documents/workload-uploads} only: an authorized backend workload (never
     * a relayed employee token) supplies the target employee explicitly, since there is no
     * caller "self" to resolve. Validated exactly like {@link UploadRequest} otherwise - no
     * additional file-type allow-list or size limit is invented here either.
     */
    public record WorkloadUploadRequest(
            @NotNull UUID ownerRef,
            @Size(max = 200) String classification,
            @NotBlank @Size(max = 100) String mimeType,
            @NotNull @Positive Long sizeBytes,
            @NotBlank @Size(max = 128) String checksum) { }

    public record VersionSummary(UUID id, int versionNumber, String objectKey, String checksum,
                                  String mimeType, long sizeBytes, ScanStatus scanStatus) { }

    public record Response(UUID id, UUID ownerRef, String classification, DocumentStatus status,
                            VersionSummary latestVersion, Instant createdAt, Instant updatedAt) { }

    /**
     * {@code downloadUrl} is a short-lived, pre-signed GET URL into private object storage
     * (SECURITY.md: "expiring signed access") - never a permanent or public object URL. It is
     * {@code null} only if the storage provider could not be reached to sign one; every other
     * field still reflects the real, storage-backed object.
     */
    public record DownloadResponse(UUID documentId, String objectKey, String checksum, String mimeType, long sizeBytes,
                                    String downloadUrl, Instant downloadUrlExpiresAt) { }
}
