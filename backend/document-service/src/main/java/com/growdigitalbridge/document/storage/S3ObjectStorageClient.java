package com.growdigitalbridge.document.storage;

import com.growdigitalbridge.document.config.ObjectStorageProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * The one real {@link ObjectStorageClient} implementation: private, non-public S3-compatible
 * storage (MinIO locally; any S3-compatible provider in a real environment) accessed through the
 * standard AWS S3 SDK. The bucket is never given a public/anonymous access policy anywhere in
 * this class - every object is only ever reachable through a request signed with this service's
 * own private credentials, either directly (put/get) or via a short-lived pre-signed URL.
 */
@Component
public class S3ObjectStorageClient implements ObjectStorageClient {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageClient.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final ObjectStorageProperties properties;

    public S3ObjectStorageClient(S3Client s3Client, S3Presigner presigner, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
    }

    /** Idempotently ensures the configured bucket exists - never grants it a public/anonymous policy. */
    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            log.info("Created private object storage bucket '{}'.", properties.bucket());
        }
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key(objectKey))
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> getObject(String objectKey) {
        try {
            return Optional.of(s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key(objectKey))
                    .build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key(objectKey)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public SignedDownload presignDownload(String objectKey, String contentType) {
        Duration expiry = properties.downloadUrlExpiry();
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key(objectKey))
                .responseContentType(contentType)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(getRequest)
                .build());
        return new SignedDownload(presigned.url().toString(), Instant.now().plus(expiry));
    }

    private String key(String objectKey) {
        String prefix = properties.keyPrefix();
        return (prefix == null || prefix.isBlank()) ? objectKey : prefix + "/" + objectKey;
    }
}
