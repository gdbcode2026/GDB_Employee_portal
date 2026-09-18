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

    public record VersionSummary(UUID id, int versionNumber, String objectKey, String checksum,
                                  String mimeType, long sizeBytes, ScanStatus scanStatus) { }

    public record Response(UUID id, UUID ownerRef, String classification, DocumentStatus status,
                            VersionSummary latestVersion, Instant createdAt, Instant updatedAt) { }

    /**
     * Deliberately carries no download URL: no object storage provider is integrated (none is
     * decided per docs/ARCHITECTURE_REVIEW.md item 3), so this proves the documented "access
     * check, scan status" authorization gate without fabricating a working download link.
     */
    public record DownloadResponse(UUID documentId, String objectKey, String checksum, String mimeType, long sizeBytes) { }
}
