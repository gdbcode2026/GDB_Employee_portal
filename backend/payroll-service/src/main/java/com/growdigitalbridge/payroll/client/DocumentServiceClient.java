package com.growdigitalbridge.payroll.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.growdigitalbridge.payroll.security.CurrentBearerToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls Document Service's existing, already-implemented contract
 * (PAYROLL_REQUIREMENTS.md Section N/U). Two distinct authentication modes, matching exactly
 * which identity is available at each call site:
 *
 * <p><b>Workload-authenticated</b> ({@link #createWorkloadUpload}/{@link #completeUpload}) - used
 * by {@code PayslipGenerationService}, a background step with no live caller. Authenticates as
 * the {@code payroll-service} workload via {@link WorkloadTokenProvider}'s OAuth2
 * client-credentials token, never a relayed employee token - this is exactly the gap Section N
 * identifies relay as unable to express ("a batch workload acting on behalf of many other
 * employees at once").
 *
 * <p><b>Relay-authenticated</b> ({@link #downloadOnBehalfOfCaller}) - used by {@code
 * PayslipService} within a live employee HTTP request. No workload identity is needed here:
 * Document Service's own {@code GET /documents/{id}/download} already authorizes by comparing
 * {@code ownerRef} to the caller's own resolved self, exactly like any other document, so the
 * caller's own relayed token works unchanged (Section N: "No new endpoint needed").
 */
@Component
public class DocumentServiceClient {

    private final RestClient restClient;
    private final WorkloadTokenProvider workloadTokenProvider;

    public DocumentServiceClient(RestClient documentServiceRestClient, WorkloadTokenProvider workloadTokenProvider) {
        this.restClient = documentServiceRestClient;
        this.workloadTokenProvider = workloadTokenProvider;
    }

    public UploadResponse createWorkloadUpload(UUID ownerRef, String classification, String mimeType, long sizeBytes, String checksum) {
        String token = workloadTokenProvider.fetchToken();
        return restClient.post()
                .uri("/api/v1/documents/workload-uploads")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new WorkloadUploadRequest(ownerRef, classification, mimeType, sizeBytes, checksum))
                .retrieve()
                .body(UploadResponse.class);
    }

    public void completeUpload(UUID documentId, String checksum) {
        String token = workloadTokenProvider.fetchToken();
        restClient.post()
                .uri("/api/v1/documents/uploads/{id}/complete", documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CompleteRequest(checksum))
                .retrieve()
                .toBodilessEntity();
    }

    /** Relays the current HTTP request's own bearer token - see class Javadoc. */
    public DownloadResponse downloadOnBehalfOfCaller(UUID documentId) {
        String token = CurrentBearerToken.resolve();
        if (token == null) {
            throw new IllegalStateException("No authenticated caller token available to relay for document download.");
        }
        return restClient.get()
                .uri("/api/v1/documents/{id}/download", documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(DownloadResponse.class);
    }

    private record WorkloadUploadRequest(UUID ownerRef, String classification, String mimeType, long sizeBytes, String checksum) { }

    private record CompleteRequest(String checksum) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadResponse(UUID id, UUID ownerRef, String classification, String status, Instant createdAt, Instant updatedAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DownloadResponse(UUID documentId, String objectKey, String checksum, String mimeType, long sizeBytes) { }
}
