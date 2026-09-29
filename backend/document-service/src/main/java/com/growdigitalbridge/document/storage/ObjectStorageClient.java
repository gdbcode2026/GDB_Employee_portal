package com.growdigitalbridge.document.storage;

import java.time.Instant;
import java.util.Optional;

/**
 * The only abstraction over document binary storage (MICROSERVICES.md: "No binary files in
 * database... private object storage"). {@code DocumentService} and its collaborators never talk
 * to an S3/MinIO client directly - everything about the storage provider is isolated behind this
 * interface, with {@link S3ObjectStorageClient} as its one real implementation.
 */
public interface ObjectStorageClient {

    /** Uploads bytes under {@code objectKey}, overwriting any existing object at that key. */
    void putObject(String objectKey, byte[] content, String contentType);

    /**
     * Fetches an object's bytes. Empty (never an exception) when the key does not exist in
     * storage - a missing object is an ordinary, safely-handled outcome, not a fault.
     */
    Optional<byte[]> getObject(String objectKey);

    /** True if an object exists at {@code objectKey}, without transferring its bytes. */
    boolean exists(String objectKey);

    /**
     * A short-lived, pre-signed GET URL for {@code objectKey} - never a permanent or public
     * object URL (SECURITY.md: "private object storage... expiring signed access").
     */
    SignedDownload presignDownload(String objectKey, String contentType);

    record SignedDownload(String url, Instant expiresAt) { }
}
